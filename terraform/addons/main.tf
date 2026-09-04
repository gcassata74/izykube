locals {
  project_root = abspath("${path.root}/../..")

  kubeconfig_path = var.kubeconfig_path == "" ? "${local.project_root}/.izykube/host-config" : (
    startswith(var.kubeconfig_path, "/")
    ? var.kubeconfig_path
    : abspath("${path.root}/${var.kubeconfig_path}")
  )

  olm_base_url            = "https://github.com/operator-framework/operator-lifecycle-manager/releases/download/${var.olm_version}"
  yaml_document_separator = "__IZYKUBE_YAML_DOCUMENT_SEPARATOR__"

  # Split only YAML document separator lines. A plain split("---") also
  # matches text inside descriptions (for example "rw-rw----" in the OLM
  # ClusterServiceVersion schema), producing invalid partial CRDs.
  olm_crd_yaml = replace(
    data.http.olm_crds.response_body,
    "/(?m)^---[ \\t]*\\r?$/",
    local.yaml_document_separator,
  )

  olm_yaml = replace(
    data.http.olm.response_body,
    "/(?m)^---[ \\t]*\\r?$/",
    local.yaml_document_separator,
  )

  # OLM publishes multi-document YAML files. Split them into individual
  # Kubernetes objects so Terraform can track each object independently.
  olm_crd_documents = {
    for index, document in split(local.yaml_document_separator, local.olm_crd_yaml) :
    tostring(index) => trimspace(document)
    if trimspace(document) != "" && can(yamldecode(trimspace(document))) && try(yamldecode(trimspace(document)).kind, "") != ""
  }

  olm_documents = {
    for index, document in split(local.yaml_document_separator, local.olm_yaml) :
    tostring(index) => trimspace(document)
    if trimspace(document) != "" && can(yamldecode(trimspace(document))) && try(yamldecode(trimspace(document)).kind, "") != ""
  }
}

data "http" "olm_crds" {
  url = "${local.olm_base_url}/crds.yaml"
}

data "http" "olm" {
  url = "${local.olm_base_url}/olm.yaml"
}

resource "kubectl_manifest" "olm_crd" {
  for_each  = local.olm_crd_documents
  yaml_body = each.value

  wait_for_rollout  = false
  server_side_apply = true
}

resource "kubectl_manifest" "olm" {
  for_each  = local.olm_documents
  yaml_body = each.value

  wait_for_rollout = false
  depends_on       = [kubectl_manifest.olm_crd]
}

resource "helm_release" "cert_manager" {
  name             = "cert-manager"
  repository       = "https://charts.jetstack.io"
  chart            = "cert-manager"
  version          = var.cert_manager_version
  namespace        = "cert-manager"
  create_namespace = true
  timeout          = 900
  atomic           = true
  cleanup_on_fail  = true

  set {
    name  = "crds.enabled"
    value = "true"
  }
}

resource "kubernetes_namespace_v1" "monitoring" {
  metadata {
    name = var.monitoring_namespace
  }
}

resource "kubernetes_namespace_v1" "izykube_system" {
  metadata {
    name = "izykube-system"
  }
}

resource "helm_release" "istio_base" {
  name             = "istio-base"
  repository       = "https://istio-release.storage.googleapis.com/charts"
  chart            = "base"
  version          = var.istio_version
  namespace        = "istio-system"
  create_namespace = true
  timeout          = 900
  atomic           = true
  cleanup_on_fail  = true
}

resource "helm_release" "istiod" {
  name            = "istiod"
  repository      = "https://istio-release.storage.googleapis.com/charts"
  chart           = "istiod"
  version         = var.istio_version
  namespace       = "istio-system"
  timeout         = 900
  atomic          = true
  cleanup_on_fail = true

  depends_on = [helm_release.istio_base]
}

resource "helm_release" "istio_ingressgateway" {
  name            = "istio-ingressgateway"
  repository      = "https://istio-release.storage.googleapis.com/charts"
  chart           = "gateway"
  version         = var.istio_version
  namespace       = "istio-system"
  timeout         = 900
  atomic          = true
  cleanup_on_fail = true

  set {
    name  = "service.type"
    value = "LoadBalancer"
  }

  depends_on = [helm_release.istiod]
}

resource "tls_private_key" "internal_ca" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "tls_self_signed_cert" "internal_ca" {
  private_key_pem       = tls_private_key.internal_ca.private_key_pem
  validity_period_hours = 87600
  is_ca_certificate     = true
  early_renewal_hours   = 720
  allowed_uses          = ["cert_signing", "crl_signing", "digital_signature"]

  subject {
    common_name  = var.ca_common_name
    organization = "IzyKube"
  }
}

resource "kubernetes_secret_v1" "internal_ca" {
  metadata {
    name      = "izykube-ca"
    namespace = "cert-manager"
  }

  type = "kubernetes.io/tls"
  data = {
    "tls.crt" = base64encode(tls_self_signed_cert.internal_ca.cert_pem)
    "tls.key" = base64encode(tls_private_key.internal_ca.private_key_pem)
  }

  depends_on = [helm_release.cert_manager]
}

resource "kubectl_manifest" "internal_ca_issuer" {
  yaml_body = yamlencode({
    apiVersion = "cert-manager.io/v1"
    kind       = "ClusterIssuer"
    metadata = {
      name = "izykube-ca-issuer"
    }
    spec = {
      ca = {
        secretName = kubernetes_secret_v1.internal_ca.metadata[0].name
      }
    }
  })

  depends_on = [kubernetes_secret_v1.internal_ca]
}

resource "kubectl_manifest" "shared_gateway" {
  yaml_body = yamlencode({
    apiVersion = "networking.istio.io/v1beta1"
    kind       = "Gateway"
    metadata = {
      name      = var.gateway_name
      namespace = var.gateway_namespace
    }
    spec = {
      selector = {
        istio = "ingressgateway"
      }
      servers = [{
        port = {
          number   = 80
          name     = "http"
          protocol = "HTTP"
        }
        hosts = ["*"]
      }]
    }
  })

  # RouteService adds host-specific HTTPS servers to this shared Gateway.
  # Terraform owns the baseline object while the application owns those
  # runtime TLS additions.
  lifecycle {
    ignore_changes = [yaml_body]
  }

  depends_on = [helm_release.istio_ingressgateway]
}

resource "helm_release" "prometheus" {
  name            = "prometheus"
  repository      = "https://prometheus-community.github.io/helm-charts"
  chart           = "kube-prometheus-stack"
  version         = var.prometheus_stack_version
  namespace       = var.monitoring_namespace
  timeout         = 900
  atomic          = true
  cleanup_on_fail = true

  values = [yamlencode({
    grafana = {
      enabled = false
    }
  })]

  depends_on = [kubernetes_namespace_v1.monitoring]
}

resource "helm_release" "grafana" {
  name            = "grafana"
  repository      = "https://grafana.github.io/helm-charts"
  chart           = "grafana"
  version         = var.grafana_version
  namespace       = var.monitoring_namespace
  timeout         = 900
  atomic          = true
  cleanup_on_fail = true

  depends_on = [kubernetes_namespace_v1.monitoring]
}
