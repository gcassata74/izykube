# IzyKube
# Copyright (c) 2026-present Izylife Solutions s.r.l.
# Author: Giuseppe Cassata
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License,
# or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.

# Makefile

# Define default shell to be used
SHELL := /bin/bash
LOCALE ?= en
OLM_VERSION ?= v0.30.0
OLM_BASE_URL ?= https://github.com/operator-framework/operator-lifecycle-manager/releases/download/$(OLM_VERSION)
ARGOCD_VERSION ?= v3.4.2
ARGOCD_INSTALL_URL ?= https://raw.githubusercontent.com/argoproj/argo-cd/$(ARGOCD_VERSION)/manifests/install.yaml
ISTIO_VERSION ?= 1.18.2
ISTIO_DIR ?= /tmp/istio-$(ISTIO_VERSION)
ISTIOCTL ?= $(ISTIO_DIR)/bin/istioctl
SUDO ?= sudo
K3D_CLUSTER ?= izycluster
K3D_CONTEXT ?= k3d-$(K3D_CLUSTER)
K3D_HOST_KUBECONFIG ?= $(CURDIR)/.izykube/kubeconfig-host
K3D_CONTAINER_KUBECONFIG ?= $(CURDIR)/.izykube/kubeconfig
K3D_KUBECONFIG ?= $(if $(wildcard /kubeconfig/config),/kubeconfig/config,$(K3D_HOST_KUBECONFIG))
export KUBECONFIG := $(K3D_KUBECONFIG)

.PHONY: setup-gui setup-gui-build retire-embedded-k3s create-k3d-cluster start-k3d-cluster prepare-k3d-kubeconfig \
	check-k3d-context start-stack stop-stack check-stack prepare-istioctl install-istio uninstall-istio check-istio \
	create-istio-system-db delete-istio-system-db install-prometheus uninstall-prometheus check-prometheus \
	install-grafana install-grafana-release uninstall-grafana check-grafana grafana-port-forward \
	install-cert-manager uninstall-cert-manager check-cert-manager install-olm check-olm uninstall-olm \
	install-argocd uninstall-argocd check-argocd \
	create-internal-ca uninstall-internal-ca check-internal-ca install-ca-local \
	install-istio-gateway uninstall-istio-gateway check-istio-gateway \
	install-cluster-addons uninstall-cluster-addons check-cluster-addons create-izykube-system

setup-gui:
	python3 -m installer.main

setup-gui-build:
	docker build --file installer/Dockerfile.pyinstaller --target artifact --output type=local,dest=dist .

retire-embedded-k3s:
	@if docker ps -a --format '{{.Names}}' | grep -qx 'izykube-k3s'; then \
		echo "Removing obsolete embedded cluster container 'izykube-k3s' (volumes are preserved)..."; \
		docker rm -f izykube-k3s >/dev/null; \
	fi

create-k3d-cluster: retire-embedded-k3s
	@command -v k3d >/dev/null || { echo "ERROR: k3d is required."; exit 1; }
	@if k3d cluster list --no-headers 2>/dev/null | awk '{print $$1}' | grep -qx '$(K3D_CLUSTER)'; then \
		echo "k3d cluster '$(K3D_CLUSTER)' already exists."; \
	else \
		k3d cluster create $(K3D_CLUSTER) \
			-p '80:80@loadbalancer' -p '443:443@loadbalancer' \
			--k3s-arg '--disable=traefik@server:*'; \
	fi

start-k3d-cluster: create-k3d-cluster
	k3d cluster start $(K3D_CLUSTER)

prepare-k3d-kubeconfig: start-k3d-cluster
	@mkdir -p "$(dir $(K3D_HOST_KUBECONFIG))"
	@k3d kubeconfig get $(K3D_CLUSTER) > "$(K3D_HOST_KUBECONFIG).tmp"
	@server_container=$$(docker inspect k3d-$(K3D_CLUSTER)-server-0 --format '{{.Name}}' | sed 's#^/##'); \
	sed -E "s#server: https://[^:]+:[0-9]+#server: https://$$server_container:6443#" \
		"$(K3D_HOST_KUBECONFIG).tmp" > "$(K3D_CONTAINER_KUBECONFIG)"; \
	mv "$(K3D_HOST_KUBECONFIG).tmp" "$(K3D_HOST_KUBECONFIG)"; \
	chmod 600 "$(K3D_HOST_KUBECONFIG)" "$(K3D_CONTAINER_KUBECONFIG)"

check-k3d-context:
	@test "$$(kubectl config current-context 2>/dev/null)" = "$(K3D_CONTEXT)" || { \
		echo "ERROR: expected Kubernetes context '$(K3D_CONTEXT)'."; exit 1; \
	}
	@kubectl get --raw=/readyz >/dev/null

start-stack: prepare-k3d-kubeconfig
	docker compose -p izykube up -d --build --remove-orphans

stop-stack:
	docker compose -p izykube down

check-stack:
	docker compose -p izykube ps

# e.g. make run-i18n-build LOCALE=fr
run-i18n-build:
	ng build --configuration=$(LOCALE)

# e.g. make run-i18n-serve LOCALE=fr
run-i18n-serve:
	ng serve --configuration=$(LOCALE)

run-i18n-extract:
	cd frontend && ng extract-i18n --output-path src/locale --format xlf

run-chrome-dev:
	google-chrome \
      --new-window "http://localhost:4200" \
      --remote-debugging-port=9222 \
      --disable-web-security \
      --no-sandbox \
      --user-data-dir="/tmp/ChromeDevSession" \
      --no-first-run \
      --no-default-browser-check

run-angular-client:
	cd frontend && npx kill-port 4200 || true && npm start

prepare-istioctl:
	@if [ ! -x "$(ISTIOCTL)" ]; then \
		echo "Downloading Istio $(ISTIO_VERSION)..."; \
		cd /tmp && curl -fsSL https://istio.io/downloadIstio | ISTIO_VERSION=$(ISTIO_VERSION) TARGET_ARCH=$$(uname -m) sh -; \
	fi

install-istio: check-k3d-context prepare-istioctl
	@echo "Installing Istio..."
	$(ISTIOCTL) install --set profile=default -y
	kubectl label namespace default istio-injection=enabled --overwrite
	@echo "Istio installation complete."

uninstall-istio: uninstall-istio-gateway
	@if kubectl get crd istiooperators.install.istio.io >/dev/null 2>&1; then \
		$(MAKE) prepare-istioctl; \
		$(ISTIOCTL) uninstall --purge -y; \
	else \
		echo "Istio CRD not present; skipping istioctl uninstall."; \
	fi
	kubectl label namespace default istio-injection- --overwrite || true

check-istio:
	kubectl -n istio-system get deployment istiod

# Create monitoring namespace
create-istio-system-db:
	kubectl create namespace istio-system-db --dry-run=client -o yaml | kubectl apply -f -

delete-istio-system-db:
	kubectl delete namespace istio-system-db --ignore-not-found=true --wait=false

# Install Prometheus stack into istio-system-db
install-prometheus: create-istio-system-db
	helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
	helm repo update
	helm upgrade --install prometheus prometheus-community/kube-prometheus-stack -n istio-system-db --create-namespace

uninstall-prometheus:
	helm uninstall prometheus -n istio-system-db --ignore-not-found

check-prometheus:
	helm status prometheus -n istio-system-db

# Install Grafana into istio-system-db
install-grafana: install-grafana-release grafana-port-forward

install-grafana-release: create-istio-system-db
	helm repo add grafana https://grafana.github.io/helm-charts
	helm repo update
	helm upgrade --install grafana grafana/grafana -n istio-system-db --create-namespace

uninstall-grafana:
	helm uninstall grafana -n istio-system-db --ignore-not-found

check-grafana:
	helm status grafana -n istio-system-db

# Start Grafana port-forward in background
grafana-port-forward: install-grafana-release
	@nohup kubectl -n istio-system-db port-forward svc/grafana 3000:80 >/tmp/izykube-grafana-pf.log 2>&1 & \
	echo $$! > /tmp/izykube-grafana-pf.pid; \
	disown || true

# Install cert-manager (CRDs + controller)
install-cert-manager:
	helm repo add jetstack https://charts.jetstack.io
	helm repo update
	helm upgrade --install cert-manager jetstack/cert-manager -n cert-manager --create-namespace --set crds.enabled=true

uninstall-cert-manager: uninstall-internal-ca
	helm uninstall cert-manager -n cert-manager --ignore-not-found
	kubectl delete namespace cert-manager --ignore-not-found=true --wait=false

check-cert-manager:
	helm status cert-manager -n cert-manager

# Install OLM (required for OperatorHub Subscriptions/CSVs)
install-olm:
	@echo "Installing OLM $(OLM_VERSION)..."
	kubectl apply --server-side --force-conflicts --validate=false -f $(OLM_BASE_URL)/crds.yaml
	@if ! kubectl get crd clusterserviceversions.operators.coreos.com >/dev/null 2>&1; then \
		echo "clusterserviceversions CRD missing, applying it explicitly..."; \
		curl -fsSL $(OLM_BASE_URL)/crds.yaml \
		| awk 'BEGIN{RS="---"; ORS="---\n"} /name: clusterserviceversions\.operators\.coreos\.com/' \
		| kubectl apply --server-side --force-conflicts --validate=false -f -; \
	fi
	@echo "Waiting for OLM CRDs..."
	kubectl wait --for=condition=Established --timeout=120s crd/clusterserviceversions.operators.coreos.com
	kubectl wait --for=condition=Established --timeout=120s crd/subscriptions.operators.coreos.com
	kubectl wait --for=condition=Established --timeout=120s crd/installplans.operators.coreos.com
	kubectl apply --server-side --force-conflicts --validate=false -f $(OLM_BASE_URL)/olm.yaml
	@echo "Waiting for OLM deployments..."
	kubectl -n olm wait --for=condition=Available deployment/olm-operator --timeout=300s
	kubectl -n olm wait --for=condition=Available deployment/catalog-operator --timeout=300s
	kubectl -n olm rollout status deployment/olm-operator --timeout=180s
	kubectl -n olm rollout status deployment/catalog-operator --timeout=180s
	kubectl -n olm rollout status deployment/packageserver --timeout=180s
	@echo "Waiting for PackageServer APIService..."
	kubectl wait --for=condition=Available apiservice/v1.packages.operators.coreos.com --timeout=300s
	@echo "OLM installation complete."

# Verify OLM status
check-olm:
	kubectl get ns olm operators
	kubectl get crd clusterserviceversions.operators.coreos.com subscriptions.operators.coreos.com installplans.operators.coreos.com
	kubectl -n olm get deploy,pods
	kubectl get apiservice v1.packages.operators.coreos.com

# Install Argo CD from a pinned upstream release.
install-argocd:
	kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
	kubectl apply -n argocd --server-side --force-conflicts -f $(ARGOCD_INSTALL_URL)
	kubectl -n argocd wait --for=condition=Available deployment --all --timeout=600s
	kubectl -n argocd rollout status statefulset/argocd-application-controller --timeout=600s

uninstall-argocd:
	kubectl delete -f $(ARGOCD_INSTALL_URL) --ignore-not-found=true --wait=false
	kubectl delete namespace argocd --ignore-not-found=true --wait=false

check-argocd:
	kubectl get namespace argocd
	kubectl -n argocd get deployment,statefulset,pods,service
	kubectl -n argocd wait --for=condition=Available deployment --all --timeout=120s
	kubectl -n argocd rollout status statefulset/argocd-application-controller --timeout=120s

# Uninstall OLM
uninstall-olm:
	@echo "Uninstalling OLM $(OLM_VERSION)..."
	@if kubectl get crd clusterserviceversions.operators.coreos.com >/dev/null 2>&1; then \
		kubectl delete -f $(OLM_BASE_URL)/olm.yaml --ignore-not-found=true --wait=false; \
		kubectl delete -f $(OLM_BASE_URL)/crds.yaml --ignore-not-found=true --wait=false; \
		kubectl delete namespace olm --ignore-not-found=true --wait=false; \
	else \
		echo "OLM CRDs not present; skipping OLM deletion."; \
	fi

# Create internal CA and ClusterIssuer for HTTPS routes
create-internal-ca: install-cert-manager
	@if kubectl -n cert-manager get secret izykube-ca >/dev/null 2>&1; then \
		echo "Internal CA already exists; keeping the current certificate."; \
	else \
		cert_dir=$$(mktemp -d); \
		trap 'rm -rf "$$cert_dir"' EXIT; \
		openssl req -x509 -newkey rsa:4096 -sha256 -nodes -days 3650 \
			-keyout "$$cert_dir/ca.key" -out "$$cert_dir/ca.crt" \
			-subj "/CN=izykube-internal-ca"; \
		kubectl -n cert-manager create secret tls izykube-ca \
			--cert="$$cert_dir/ca.crt" \
			--key="$$cert_dir/ca.key"; \
	fi
	kubectl apply -f yaml/izykube-ca-issuer.yaml

uninstall-internal-ca:
	@if kubectl api-resources --api-group=cert-manager.io 2>/dev/null | awk '$$1 == "clusterissuers" { found = 1 } END { exit found ? 0 : 1 }'; then \
		kubectl delete -f yaml/izykube-ca-issuer.yaml --ignore-not-found=true; \
	else \
		echo "ClusterIssuer CRD not present; skipping internal CA deletion."; \
	fi
	kubectl -n cert-manager delete secret izykube-ca --ignore-not-found=true

check-internal-ca:
	kubectl -n cert-manager get secret izykube-ca
	kubectl get clusterissuer izykube-ca-issuer

# Install internal CA certificate locally (Ubuntu/Debian)
install-ca-local: create-internal-ca
	@set -e; \
	if [ "$$(id -u)" -eq 0 ]; then SUDO_CMD=""; \
	elif $(SUDO) -n true >/dev/null 2>&1; then SUDO_CMD="$(SUDO)"; \
	elif [ -t 0 ]; then \
		echo "sudo access is required to install the CA in the system trust store."; \
		$(SUDO) -v; \
		SUDO_CMD="$(SUDO)"; \
	else \
		echo "ERROR: sudo password is required but no interactive terminal is available."; \
		echo "Run 'sudo -v && make install-ca-local' in a terminal, or run 'sudo make install-ca-local'."; \
		exit 1; \
	fi; \
	$$SUDO_CMD mkdir -p /usr/local/share/ca-certificates; \
	kubectl -n cert-manager get secret izykube-ca -o jsonpath='{.data.tls\.crt}' | base64 -d | $$SUDO_CMD tee /usr/local/share/ca-certificates/izykube-ca.crt >/dev/null; \
	$$SUDO_CMD update-ca-certificates

# Create shared Istio Gateway in istio-system
install-istio-gateway: install-istio
	kubectl apply -f yaml/izykube-gateway.yaml

uninstall-istio-gateway:
	@if kubectl api-resources --api-group=networking.istio.io 2>/dev/null | awk '$$1 == "gateways" { found = 1 } END { exit found ? 0 : 1 }'; then \
		kubectl delete -f yaml/izykube-gateway.yaml --ignore-not-found=true; \
	else \
		echo "Istio Gateway CRD not present; skipping gateway deletion."; \
	fi

check-istio-gateway:
	kubectl -n istio-system get gateway izykube-gateway

# Install all cluster addons (GitOps, operators, mesh, monitoring)
install-cluster-addons: install-argocd install-olm create-internal-ca install-istio-gateway install-prometheus install-grafana-release

uninstall-cluster-addons:
	$(MAKE) uninstall-grafana
	$(MAKE) uninstall-prometheus
	$(MAKE) delete-istio-system-db
	$(MAKE) uninstall-istio
	$(MAKE) uninstall-cert-manager
	$(MAKE) uninstall-olm
	$(MAKE) uninstall-argocd

check-cluster-addons: check-argocd check-olm check-cert-manager check-internal-ca check-istio check-istio-gateway check-prometheus check-grafana

# Create izykube system namespace
create-izykube-system:
	kubectl create namespace izykube-system --dry-run=client -o yaml | kubectl apply -f -
