# Makefile

# Define default shell to be used
SHELL := /bin/bash
LOCALE ?= en
OLM_VERSION ?= v0.30.0
OLM_BASE_URL ?= https://github.com/operator-framework/operator-lifecycle-manager/releases/download/$(OLM_VERSION)

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

run-spring-boot-server:
	./mvnw -pl backend -am -DskipTests clean package
	java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar backend/target/backend-0.0.1-SNAPSHOT.jar

create-docker-registry:
	docker run -d --name izyregistry -p 5000:5000 --restart=always registry:2

create-k3d-registry:
	k3d registry create izyregistry --port 5000

delete-docker-registry:
	docker stop izyregistry && docker rm -v izyregistry

delete-k3d-registry:
	k3d registry delete izyregistry

create-k3d-cluster:
	k3d cluster create izycluster --registry-use izyregistry:5000  -p '80:80@loadbalancer' -p '443:443@loadbalancer' --k3s-arg '--disable=traefik@server:*'

delete-k3d-cluster:
	k3d cluster delete izycluster

start-k3d-cluster: create-k3d-registry create-k3d-cluster

restart-k3d-cluster: delete-k3d-cluster create-k3d-registry create-k3d-cluster

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

# Updated target to include Istio installation
start-k3d-cluster-with-istio: create-k3d-registry create-k3d-cluster install-istio

# Create monitoring namespace
create-istio-system-db:
	kubectl create namespace istio-system-db --dry-run=client -o yaml | kubectl apply -f -

# Install Prometheus stack into istio-system-db
install-prometheus:
	helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
	helm repo update
	helm install prometheus prometheus-community/kube-prometheus-stack -n istio-system-db --create-namespace

# Install Grafana into istio-system-db
install-grafana:
	helm repo add grafana https://grafana.github.io/helm-charts
	helm repo update
	helm install grafana grafana/grafana -n istio-system-db --create-namespace
	$(MAKE) grafana-port-forward

# Start Grafana port-forward in background
grafana-port-forward:
	@nohup kubectl -n istio-system-db port-forward svc/grafana 3000:80 >/tmp/izykube-grafana-pf.log 2>&1 & \
	echo $$! > /tmp/izykube-grafana-pf.pid; \
	disown || true

# Install Ollama in-cluster (lightweight model)
install-ollama:
	kubectl apply -f yaml/ollama.yaml
# temporary port-forward until izykube is not deployend into the cluster
	kubectl -n izykube-system port-forward svc/ollama 11434:11434 >/tmp/ollama-pf.log 2>&1 &

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
create-internal-ca:
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
install-ca-local:
	sudo mkdir -p /usr/local/share/ca-certificates
	kubectl -n cert-manager get secret izykube-ca -o jsonpath='{.data.tls\.crt}' | base64 -d | sudo tee /usr/local/share/ca-certificates/izykube-ca.crt >/dev/null
	sudo update-ca-certificates

# Create shared Istio Gateway in istio-system
install-istio-gateway:
	kubectl apply -f yaml/izykube-gateway.yaml

# Install all cluster addons (istio, monitoring)
install-cluster-addons:
	$(MAKE) install-olm
	$(MAKE) install-cert-manager
	$(MAKE) create-internal-ca
	$(MAKE) install-istio
	$(MAKE) install-istio-gateway
	$(MAKE) create-istio-system-db
	$(MAKE) install-prometheus
	$(MAKE) install-grafana
	$(MAKE) create-izykube-system
	$(MAKE) install-ollama

# Bootstrap cluster with addons
bootstrap-k3d-cluster:
	$(MAKE) start-k3d-cluster
	$(MAKE) install-cluster-addons

# Create izykube system namespace
create-izykube-system:
	kubectl create namespace izykube-system --dry-run=client -o yaml | kubectl apply -f -

build-backend:
	./mvnw -pl backend -am -DskipTests clean package
	java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar backend/target/backend-0.0.1-SNAPSHOT.jar
