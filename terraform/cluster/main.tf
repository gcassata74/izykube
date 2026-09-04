locals {
  project_root = abspath("${path.root}/../..")

  kubeconfig_directory = var.kubeconfig_directory == "" ? "${local.project_root}/.izykube" : (
    startswith(var.kubeconfig_directory, "/")
    ? var.kubeconfig_directory
    : abspath("${path.root}/${var.kubeconfig_directory}")
  )

  registries_config = "${local.project_root}/k3s-registries.yaml"
}

resource "terraform_data" "kubeconfig_directory" {
  input = local.kubeconfig_directory

  provisioner "local-exec" {
    command = "mkdir -p -- \"${self.input}\""
  }
}

resource "docker_network" "izykube" {
  name   = var.network_name
  driver = "bridge"
}

resource "docker_volume" "registry_data" {
  name = "${var.network_name}_registry-data"
}

resource "docker_volume" "k3s_server" {
  name = "${var.network_name}_k3s-server"
}

resource "docker_image" "registry" {
  name = var.registry_image
}

resource "docker_container" "registry" {
  name    = var.registry_name
  image   = docker_image.registry.image_id
  restart = "unless-stopped"

  ports {
    internal = 5000
    external = var.registry_port
  }

  volumes {
    volume_name    = docker_volume.registry_data.name
    container_path = "/var/lib/registry"
  }

  networks_advanced {
    name    = docker_network.izykube.name
    aliases = ["registry"]
  }
}

resource "docker_image" "k3s" {
  name = var.k3s_image
}

resource "docker_container" "k3s" {
  name          = var.k3s_name
  image         = docker_image.k3s.image_id
  privileged    = true
  cgroupns_mode = "host"
  restart       = "unless-stopped"

  ports {
    internal = 6443
    external = var.kubernetes_api_port
  }

  ports {
    internal = 80
    external = var.http_port
  }

  ports {
    internal = 443
    external = var.https_port
  }

  volumes {
    volume_name    = docker_volume.k3s_server.name
    container_path = "/var/lib/rancher/k3s"
  }

  volumes {
    host_path      = local.kubeconfig_directory
    container_path = "/kubeconfig"
  }

  volumes {
    host_path      = local.registries_config
    container_path = "/etc/rancher/k3s/registries.yaml"
    read_only      = true
  }

  entrypoint = ["/bin/sh", "-c"]
  command = [<<-EOT
    rm -f /kubeconfig/config /kubeconfig/config.tmp /kubeconfig/host-config /kubeconfig/host-config.tmp
    k3s server \
      --disable=traefik \
      --node-name=izykube-k3s \
      --tls-san=k3s &
    K3S_PID=$!
    echo "Waiting for kubeconfig..."
    until [ -s /etc/rancher/k3s/k3s.yaml ]; do sleep 2; done
    cp /etc/rancher/k3s/k3s.yaml /kubeconfig/host-config.tmp
    chmod 644 /kubeconfig/host-config.tmp
    mv /kubeconfig/host-config.tmp /kubeconfig/host-config
    sed 's|https://127.0.0.1:6443|https://k3s:6443|g' \
      /etc/rancher/k3s/k3s.yaml > /kubeconfig/config.tmp
    chmod 644 /kubeconfig/config.tmp
    mv /kubeconfig/config.tmp /kubeconfig/config
    echo "kubeconfig ready"
    wait "$K3S_PID"
  EOT
  ]

  healthcheck {
    test         = ["CMD-SHELL", "[ -f /kubeconfig/config ] && kubectl --kubeconfig /kubeconfig/config get --raw=/readyz >/dev/null"]
    interval     = "5s"
    timeout      = "5s"
    retries      = 60
    start_period = "20s"
  }

  networks_advanced {
    name    = docker_network.izykube.name
    aliases = ["k3s"]
  }

  depends_on = [
    docker_container.registry,
    terraform_data.kubeconfig_directory,
  ]
}

resource "terraform_data" "k3s_ready" {
  depends_on = [docker_container.k3s]

  provisioner "local-exec" {
    command = <<-EOT
      timeout 300 sh -c '
        until [ "$(docker inspect -f '\''{{.State.Health.Status}}'\'' ${docker_container.k3s.id} 2>/dev/null)" = healthy ]; do
          status="$(docker inspect -f '\''{{.State.Health.Status}}'\'' ${docker_container.k3s.id} 2>/dev/null || true)"
          if [ "$status" = unhealthy ]; then
            docker logs --tail 100 ${docker_container.k3s.id} >&2
            exit 1
          fi
          sleep 2
        done
      '
    EOT
  }
}
