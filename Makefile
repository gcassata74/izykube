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
ISTIO_VERSION ?= 1.18.2
ISTIO_DIR ?= /tmp/istio-$(ISTIO_VERSION)
ISTIOCTL ?= $(ISTIO_DIR)/bin/istioctl
SUDO ?= sudo

.PHONY: setup-gui setup-gui-build prepare-istioctl install-istio uninstall-istio check-istio \
	create-istio-system-db delete-istio-system-db install-prometheus uninstall-prometheus check-prometheus \
	install-grafana install-grafana-release uninstall-grafana check-grafana grafana-port-forward \
	install-cert-manager uninstall-cert-manager check-cert-manager install-olm check-olm uninstall-olm \
	create-internal-ca uninstall-internal-ca check-internal-ca install-ca-local \
	install-istio-gateway uninstall-istio-gateway check-istio-gateway \
	install-cluster-addons uninstall-cluster-addons check-cluster-addons create-izykube-system

setup-gui:
	python3 -m installer.main

setup-gui-build:
	docker build --file installer/Dockerfile.pyinstaller --target artifact --output type=local,dest=dist .

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

install-istio: prepare-istioctl
	@echo "Installing Istio..."
	$(ISTIOCTL) install --set profile=default -y
	kubectl label namespace default istio-injection=enabled --overwrite
	@echo "Istio installation complete."

uninstall-istio: uninstall-istio-gateway prepare-istioctl
	$(ISTIOCTL) uninstall --purge -y
	kubectl label namespace default istio-injection- --overwrite || true

check-istio:
	kubectl -n istio-system get deployment istiod

# Create monitoring namespace
create-istio-system-db:
	kubectl create namespace istio-system-db --dry-run=client -o yaml | kubectl apply -f -

delete-istio-system-db:
	kubectl delete namespace istio-system-db --ignore-not-found=true

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
	kubectl delete namespace cert-manager --ignore-not-found=true

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

# Uninstall OLM
uninstall-olm:
	@echo "Uninstalling OLM $(OLM_VERSION)..."
	kubectl delete -f $(OLM_BASE_URL)/olm.yaml --ignore-not-found=true
	kubectl delete -f $(OLM_BASE_URL)/crds.yaml --ignore-not-found=true

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
	kubectl delete -f yaml/izykube-ca-issuer.yaml --ignore-not-found=true
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
	kubectl delete -f yaml/izykube-gateway.yaml --ignore-not-found=true

check-istio-gateway:
	kubectl -n istio-system get gateway izykube-gateway

# Install all cluster addons (istio, monitoring)
install-cluster-addons: install-olm create-internal-ca install-istio-gateway install-prometheus install-grafana-release

uninstall-cluster-addons:
	$(MAKE) uninstall-grafana
	$(MAKE) uninstall-prometheus
	$(MAKE) delete-istio-system-db
	$(MAKE) uninstall-istio
	$(MAKE) uninstall-cert-manager
	$(MAKE) uninstall-olm

check-cluster-addons: check-olm check-cert-manager check-internal-ca check-istio check-istio-gateway check-prometheus check-grafana

# Create izykube system namespace
create-izykube-system:
	kubectl create namespace izykube-system --dry-run=client -o yaml | kubectl apply -f -
