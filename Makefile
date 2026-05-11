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
SUDO ?= sudo

.PHONY: install-grafana install-cluster-addons

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

# New target for installing Istio
install-istio:
	@echo "Installing Istio..."
	@if ! command -v istioctl &> /dev/null; then \
		echo "istioctl not found. Downloading..."; \
		curl -L https://istio.io/downloadIstio | ISTIO_VERSION=1.18.2 sh -; \
	fi
	./istio-1.18.2/bin/istioctl install --set profile=default -y
	kubectl label namespace default istio-injection=enabled --overwrite
	@echo "Istio installation complete."
	rm -rf istio-1.18.2

# Create monitoring namespace
create-istio-system-db:
	kubectl create namespace istio-system-db --dry-run=client -o yaml | kubectl apply -f -

# Install Prometheus stack into istio-system-db
install-prometheus: create-istio-system-db
	helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
	helm repo update
	helm install prometheus prometheus-community/kube-prometheus-stack -n istio-system-db --create-namespace

# Install Grafana into istio-system-db
install-grafana: install-grafana-release grafana-port-forward

install-grafana-release: create-istio-system-db
	helm repo add grafana https://grafana.github.io/helm-charts
	helm repo update
	helm install grafana grafana/grafana -n istio-system-db --create-namespace

# Start Grafana port-forward in background
grafana-port-forward: install-grafana-release
	@nohup kubectl -n istio-system-db port-forward svc/grafana 3000:80 >/tmp/izykube-grafana-pf.log 2>&1 & \
	echo $$! > /tmp/izykube-grafana-pf.pid; \
	disown || true

# Install cert-manager (CRDs + controller)
install-cert-manager:
	helm repo add jetstack https://charts.jetstack.io
	helm repo update
	helm install cert-manager jetstack/cert-manager -n cert-manager --create-namespace --set crds.enabled=true

# Install OLM (required for OperatorHub Subscriptions/CSVs)
install-olm:
	@echo "Installing OLM $(OLM_VERSION)..."
	kubectl apply --server-side --force-conflicts -f $(OLM_BASE_URL)/crds.yaml
	@if ! kubectl get crd clusterserviceversions.operators.coreos.com >/dev/null 2>&1; then \
		echo "clusterserviceversions CRD missing, applying it explicitly..."; \
		curl -fsSL $(OLM_BASE_URL)/crds.yaml \
		| awk 'BEGIN{RS="---"; ORS="---\n"} /name: clusterserviceversions\.operators\.coreos\.com/' \
		| kubectl apply --server-side --force-conflicts -f -; \
	fi
	@echo "Waiting for OLM CRDs..."
	kubectl wait --for=condition=Established --timeout=120s crd/clusterserviceversions.operators.coreos.com
	kubectl wait --for=condition=Established --timeout=120s crd/subscriptions.operators.coreos.com
	kubectl wait --for=condition=Established --timeout=120s crd/installplans.operators.coreos.com
	kubectl apply --server-side --force-conflicts -f $(OLM_BASE_URL)/olm.yaml
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
	@mkdir -p .certs
	@openssl req -x509 -newkey rsa:4096 -sha256 -nodes -days 3650 \
		-keyout .certs/ca.key -out .certs/ca.crt \
		-subj "/CN=izykube-internal-ca"
	kubectl -n cert-manager create secret tls izykube-ca \
		--cert=.certs/ca.crt \
		--key=.certs/ca.key \
		--dry-run=client -o yaml | kubectl apply -f -
	kubectl apply -f yaml/izykube-ca-issuer.yaml
	@rm -rf .certs

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

# Install all cluster addons (istio, monitoring)
install-cluster-addons: install-olm create-internal-ca install-istio-gateway install-prometheus install-grafana

# Create izykube system namespace
create-izykube-system:
	kubectl create namespace izykube-system --dry-run=client -o yaml | kubectl apply -f -

