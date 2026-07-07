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

ifneq (,$(wildcard .env))
include .env
export
endif

LOCALE ?= en
OLM_VERSION ?= v0.30.0
OLM_BASE_URL ?= https://github.com/operator-framework/operator-lifecycle-manager/releases/download/$(OLM_VERSION)
SUDO ?= sudo
DEFAULT_OLLAMA_MODEL ?= $(shell awk '/^[[:space:]]*model:/ { print $$2; exit }' backend/src/main/resources/application.yaml 2>/dev/null)
DEFAULT_OLLAMA_MODEL := $(if $(strip $(DEFAULT_OLLAMA_MODEL)),$(DEFAULT_OLLAMA_MODEL),llama3)
OLLAMA_MODEL ?=
OLLAMA_CONTAINER ?= izykube-ollama
ARGOCD_NS ?= argocd
ARGOCD_INSTALL_URL ?= https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
PYTHON ?= python3
REQUIREMENTS_FILE ?= requirements.txt
VENV_DIR ?= .venv
VENV_PYTHON ?= $(VENV_DIR)/bin/python
VENV_PIP ?= $(VENV_PYTHON) -m pip
GITEA_CONTAINER ?= izykube-gitea
GITEA_USER ?= izykube
GITEA_PASSWORD ?=
GITEA_EMAIL ?= izykube@local
GITEA_REPO ?= izykube-gitops
GITEA_HTTP_URL ?= http://localhost:3001
GITOPS_USERNAME ?= $(GITEA_USER)
GITOPS_PASSWORD ?= $(GITEA_PASSWORD)

.PHONY: install uninstall install-core install-python-deps bootstrap-gitea \
	check-kube-connection \
	install-olm install-cert-manager create-internal-ca \
	install-istio install-istio-gateway \
	install-prometheus install-grafana install-grafana-release grafana-port-forward \
	install-argocd install-cluster-addons \
	setup-ollama \
	uninstall-olm uninstall-cert-manager uninstall-internal-ca \
	uninstall-istio uninstall-istio-gateway \
	uninstall-prometheus uninstall-grafana-release \
	uninstall-argocd uninstall-cluster-addons \
	check-olm create-istio-system-db create-izykube-system

# ============================================================
#  Entry points
# ============================================================

install:
	@$(MAKE) install-python-deps
	@$(VENV_PYTHON) ./scripts/installer.py install

uninstall:
	@$(MAKE) install-python-deps
	@$(VENV_PYTHON) ./scripts/installer.py uninstall

install-python-deps:
	@echo "Installing Python dependencies from $(REQUIREMENTS_FILE)..."
	@test -d $(VENV_DIR) || $(PYTHON) -m venv $(VENV_DIR)
	@$(VENV_PIP) install --upgrade pip
	@$(VENV_PIP) install -r $(REQUIREMENTS_FILE)

bootstrap-gitea:
	@if [ -z "$(GITEA_PASSWORD)" ]; then echo "ERROR: set GITEA_PASSWORD in .env (see .env.example)"; exit 1; fi
	@echo "Starting local Gitea service..."
	@docker compose up -d gitea
	@echo "Waiting for Gitea API..."
	@for i in $$(seq 1 40); do \
		if curl -fsS $(GITEA_HTTP_URL)/api/healthz >/dev/null 2>&1; then break; fi; \
		sleep 2; \
		if [ $$i -eq 40 ]; then echo "ERROR: Gitea is not ready"; exit 1; fi; \
	done
	@echo "Ensuring Gitea user $(GITEA_USER) exists..."
	@docker exec $(GITEA_CONTAINER) sh -c 'gitea admin user create --username "$(GITEA_USER)" --password "$(GITEA_PASSWORD)" --email "$(GITEA_EMAIL)" --must-change-password=false >/dev/null 2>&1 || true'
	@echo "Ensuring Gitea repo $(GITEA_REPO) exists..."
	@curl -fsS -u "$(GITEA_USER):$(GITEA_PASSWORD)" \
		-X POST "$(GITEA_HTTP_URL)/api/v1/user/repos" \
		-H "Content-Type: application/json" \
		-d '{"name":"$(GITEA_REPO)","private":false,"auto_init":true}' >/dev/null 2>&1 || true
	@echo "Local GitOps repository ready: $(GITEA_HTTP_URL)/$(GITEA_USER)/$(GITEA_REPO).git"

# ============================================================
#  Cluster connectivity check
# ============================================================

check-kube-connection:
	@CTX="$$(kubectl config current-context 2>/dev/null || true)"; \
	if ! kubectl cluster-info >/dev/null 2>&1; then \
		if [[ "$$CTX" == k3d-* ]] && command -v k3d >/dev/null 2>&1; then \
			CLUSTER="$${CTX#k3d-}"; \
			echo "Kubernetes API unreachable. Trying to start k3d cluster '$$CLUSTER'..."; \
			k3d cluster start "$$CLUSTER" >/dev/null 2>&1 || true; \
			k3d kubeconfig merge "$$CLUSTER" --kubeconfig-switch-context >/dev/null 2>&1 || true; \
			sleep 2; \
		fi; \
	fi; \
	kubectl cluster-info >/dev/null 2>&1 || { \
		echo "ERROR: kubectl cannot reach the Kubernetes API server."; \
		echo "Current context: $$CTX"; \
		echo ""; \
		echo "Fix one of these and retry:"; \
		echo "1) If you use docker compose k3s:"; \
		echo "   export KUBECONFIG=$$(docker volume inspect izykube_kubeconfig --format '{{.Mountpoint}}' 2>/dev/null)/config"; \
		echo "2) If you use k3d:"; \
		echo "   k3d cluster start $${CTX#k3d-}"; \
		echo "   k3d kubeconfig merge $${CTX#k3d-} --kubeconfig-switch-context"; \
		echo "3) Verify the cluster is up:"; \
		echo "   kubectl get nodes"; \
		exit 1; \
	}

# ============================================================
#  Install steps (each is idempotent — safe to re-run)
# ============================================================

install-core: check-kube-connection install-cluster-addons install-argocd
	@echo "Ensuring Ollama model $(OLLAMA_MODEL) is available..."
	@if command -v ollama >/dev/null 2>&1; then \
		ollama pull "$(OLLAMA_MODEL)"; \
	elif docker ps --format '{{.Names}}' | grep -qx "$(OLLAMA_CONTAINER)"; then \
		docker exec "$(OLLAMA_CONTAINER)" ollama pull "$(OLLAMA_MODEL)"; \
	else \
		echo "WARNING: ollama CLI not found and container $(OLLAMA_CONTAINER) is not running. Skipping model pull."; \
	fi

setup-ollama:
	@echo "Pulling Ollama model: $(OLLAMA_MODEL)..."
	@if command -v ollama >/dev/null 2>&1; then \
		ollama pull "$(OLLAMA_MODEL)"; \
	elif docker ps --format '{{.Names}}' | grep -qx "$(OLLAMA_CONTAINER)"; then \
		docker exec "$(OLLAMA_CONTAINER)" ollama pull "$(OLLAMA_MODEL)"; \
	else \
		echo "WARNING: ollama CLI not found and container $(OLLAMA_CONTAINER) is not running. Skipping model pull."; \
	fi

# --- OLM ---
install-olm:
	@echo "Installing OLM $(OLM_VERSION)..."
	@# Handle stuck terminating namespace from previous uninstall
	@if kubectl get ns olm -o jsonpath='{.status.phase}' 2>/dev/null | grep -q Terminating; then \
		echo "olm namespace is terminating, cleaning up..."; \
		kubectl delete apiservice v1.packages.operators.coreos.com --ignore-not-found=true 2>/dev/null || true; \
		kubectl -n olm get csv -o name 2>/dev/null | xargs -I {} kubectl -n olm patch {} --type=merge -p '{"metadata":{"finalizers":[]}}' 2>/dev/null || true; \
		echo "Waiting for olm namespace removal (max 60s)..."; \
		for i in $$(seq 1 12); do \
			kubectl get ns olm >/dev/null 2>&1 || break; \
			sleep 5; \
		done; \
		if kubectl get ns olm >/dev/null 2>&1; then \
			echo "Force-removing namespace finalizers..."; \
			kubectl get ns olm -o json | jq '.spec.finalizers=[]' | kubectl replace --raw "/api/v1/namespaces/olm/finalize" -f - 2>/dev/null || true; \
			sleep 5; \
		fi; \
	fi
	@kubectl apply --server-side --force-conflicts -f $(OLM_BASE_URL)/crds.yaml
	@if ! kubectl get crd clusterserviceversions.operators.coreos.com >/dev/null 2>&1; then \
		echo "clusterserviceversions CRD missing, applying it explicitly..."; \
		curl -fsSL $(OLM_BASE_URL)/crds.yaml \
		| awk 'BEGIN{RS="---"; ORS="---\n"} /name: clusterserviceversions\.operators\.coreos\.com/' \
		| kubectl apply --server-side --force-conflicts -f -; \
	fi
	@echo "Waiting for OLM CRDs (max 2 minutes)..."
	@for crd in clusterserviceversions.operators.coreos.com subscriptions.operators.coreos.com installplans.operators.coreos.com; do \
		kubectl wait --for=condition=Established --timeout=120s crd/$$crd || exit 1; \
	done
	@kubectl apply --server-side --force-conflicts -f $(OLM_BASE_URL)/olm.yaml
	@echo "Waiting for OLM deployments..."
	@for deploy in olm-operator catalog-operator; do \
		kubectl -n olm wait --for=condition=Available deployment/$$deploy --timeout=300s || exit 1; \
		kubectl -n olm rollout status deployment/$$deploy --timeout=180s || exit 1; \
	done
	@kubectl -n olm rollout status deployment/packageserver --timeout=180s || exit 1
	@echo "Waiting for PackageServer APIService..."
	@kubectl wait --for=condition=Available apiservice/v1.packages.operators.coreos.com --timeout=300s || exit 1
	@echo "OLM installation complete."

check-olm:
	kubectl get ns olm operators
	kubectl get crd clusterserviceversions.operators.coreos.com subscriptions.operators.coreos.com installplans.operators.coreos.com
	kubectl -n olm get deploy,pods
	kubectl get apiservice v1.packages.operators.coreos.com

# --- cert-manager ---
install-cert-manager:
	@echo "Installing cert-manager..."
	@helm repo add jetstack https://charts.jetstack.io || true
	@helm repo update
	helm upgrade --install cert-manager jetstack/cert-manager \
		-n cert-manager --create-namespace --set crds.enabled=true --wait --timeout 180s
	@echo "cert-manager installed."

# --- Internal CA ---
create-internal-ca:
	@echo "Creating internal CA..."
	@$(MAKE) install-cert-manager
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
	@echo "Internal CA created."

# --- Istio ---
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

install-istio-gateway:
	@echo "Installing Istio Gateway..."
	@$(MAKE) install-istio
	kubectl apply -f yaml/izykube-gateway.yaml
	@echo "Istio Gateway installed."

# --- Monitoring namespace ---
create-istio-system-db:
	kubectl create namespace istio-system-db --dry-run=client -o yaml | kubectl apply -f -

# --- Prometheus ---
install-prometheus:
	@echo "Installing Prometheus..."
	@$(MAKE) create-istio-system-db
	@helm repo add prometheus-community https://prometheus-community.github.io/helm-charts || true
	@helm repo update
	helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
		-n istio-system-db --create-namespace --wait --timeout 300s
	@echo "Prometheus installed."

# --- Grafana ---
install-grafana: install-grafana-release grafana-port-forward

install-grafana-release:
	@echo "Installing Grafana..."
	@$(MAKE) create-istio-system-db
	@helm repo add grafana https://grafana.github.io/helm-charts || true
	@helm repo update
	helm upgrade --install grafana grafana/grafana \
		-n istio-system-db --create-namespace --wait --timeout 180s
	@echo "Grafana installed."

grafana-port-forward: install-grafana-release
	@nohup kubectl -n istio-system-db port-forward svc/grafana 3000:80 >/tmp/izykube-grafana-pf.log 2>&1 & \
	echo $$! > /tmp/izykube-grafana-pf.pid; \
	disown || true

# --- Argo CD ---
install-argocd:
	@echo "Installing Argo CD..."
	@kubectl create namespace $(ARGOCD_NS) --dry-run=client -o yaml | kubectl apply -f -
	@if kubectl -n $(ARGOCD_NS) get deploy/argocd-server >/dev/null 2>&1; then \
		echo "Argo CD already installed, upgrading..."; \
	fi
	@kubectl apply -n $(ARGOCD_NS) --server-side --force-conflicts -f $(ARGOCD_INSTALL_URL)
	@echo "Waiting for Argo CD server..."
	@kubectl -n $(ARGOCD_NS) rollout status deploy/argocd-server --timeout=300s || exit 1
	@mkdir -p .secrets
	@kubectl -n $(ARGOCD_NS) get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" 2>/dev/null | base64 -d > .secrets/argocd-admin-password || echo "unknown" > .secrets/argocd-admin-password
	@echo "Argo CD installed. Admin password saved to .secrets/argocd-admin-password"

# --- All cluster addons ---
install-cluster-addons: install-olm create-internal-ca install-istio-gateway install-prometheus install-grafana

# ============================================================
#  Uninstall steps (each tolerates missing resources)
# ============================================================

uninstall-olm:
	@echo "Uninstalling OLM $(OLM_VERSION)..."
	@# Remove APIService first to prevent namespace stuck in Terminating
	@kubectl delete apiservice v1.packages.operators.coreos.com --ignore-not-found=true 2>/dev/null || true
	@# Remove finalizers from CSVs
	@kubectl -n olm get csv -o name 2>/dev/null | xargs -I {} kubectl -n olm patch {} --type=merge -p '{"metadata":{"finalizers":[]}}' 2>/dev/null || true
	@kubectl delete -f $(OLM_BASE_URL)/olm.yaml --ignore-not-found=true 2>/dev/null || true
	@kubectl delete -f $(OLM_BASE_URL)/crds.yaml --ignore-not-found=true 2>/dev/null || true
	@kubectl delete namespace olm --ignore-not-found=true 2>/dev/null || true
	@kubectl delete namespace operators --ignore-not-found=true 2>/dev/null || true
	@# If olm namespace is still terminating, force finalization to avoid hanging uninstall.
	@if kubectl get ns olm -o jsonpath='{.status.phase}' 2>/dev/null | grep -q Terminating; then \
		echo "Namespace olm still terminating, forcing finalizers removal..."; \
		kubectl delete apiservice v1.packages.operators.coreos.com --ignore-not-found=true 2>/dev/null || true; \
		kubectl -n olm get csv -o name 2>/dev/null | xargs -I {} kubectl -n olm patch {} --type=merge -p '{"metadata":{"finalizers":[]}}' 2>/dev/null || true; \
		kubectl get ns olm -o json 2>/dev/null | jq '.spec.finalizers=[]' | kubectl replace --raw "/api/v1/namespaces/olm/finalize" -f - 2>/dev/null || true; \
		for i in $$(seq 1 10); do \
			kubectl get ns olm >/dev/null 2>&1 || break; \
			sleep 2; \
		done; \
	fi

uninstall-prometheus:
	@echo "Uninstalling Prometheus..."
	@helm uninstall prometheus -n istio-system-db 2>/dev/null || true

uninstall-grafana-release:
	@echo "Uninstalling Grafana..."
	@helm uninstall grafana -n istio-system-db 2>/dev/null || true
	@if [ -f /tmp/izykube-grafana-pf.pid ]; then \
		kill $$(cat /tmp/izykube-grafana-pf.pid) 2>/dev/null || true; \
		rm -f /tmp/izykube-grafana-pf.pid; \
	fi

uninstall-istio-gateway:
	@echo "Uninstalling Istio Gateway..."
	@kubectl delete -f yaml/izykube-gateway.yaml --ignore-not-found=true || true

uninstall-internal-ca:
	@echo "Uninstalling internal CA..."
	@kubectl delete -f yaml/izykube-ca-issuer.yaml --ignore-not-found=true || true
	@kubectl -n cert-manager delete secret izykube-ca --ignore-not-found=true || true

uninstall-cert-manager:
	@echo "Uninstalling cert-manager..."
	@helm uninstall cert-manager -n cert-manager 2>/dev/null || true
	@kubectl delete namespace cert-manager --ignore-not-found=true || true

uninstall-istio:
	@echo "Uninstalling Istio..."
	@if command -v istioctl >/dev/null 2>&1; then istioctl uninstall -y --purge || true; fi
	@kubectl label namespace default istio-injection- --overwrite 2>/dev/null || true
	@kubectl delete namespace istio-system --ignore-not-found=true || true

uninstall-argocd:
	@echo "Uninstalling Argo CD..."
	@kubectl delete namespace $(ARGOCD_NS) --ignore-not-found=true || true
	@rm -f .secrets/argocd-admin-password

uninstall-cluster-addons: uninstall-argocd uninstall-prometheus uninstall-grafana-release uninstall-istio-gateway uninstall-internal-ca uninstall-cert-manager uninstall-istio uninstall-olm
	@kubectl delete namespace istio-system-db --ignore-not-found=true || true
	@echo "All cluster addons uninstalled."

# ============================================================
#  Utilities
# ============================================================

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

create-izykube-system:
	kubectl create namespace izykube-system --dry-run=client -o yaml | kubectl apply -f -

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

