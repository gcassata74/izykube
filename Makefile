# Makefile

# Define default shell to be used
SHELL := /bin/bash
LOCALE ?= en

# e.g. make run-i18n-build LOCALE=fr
run-i18n-build:
	ng build --configuration=$(LOCALE)

# e.g. make run-i18n-serve LOCALE=fr
run-i18n-serve:
	ng serve --configuration=$(LOCALE)

run-i18n-extract:
	cd frontend && ng extract-i18n --output-path src/locale --format xlf

run-chrome-dev:
	google-chrome --new-window "http://127.0.0.1:4200" --remote-debugging-port=9222 --disable-web-security --user-data-dir="~/ChromeDevSession"

run-angular-client:
	cd frontend && npx kill-port 4200 || true && npm start

run-spring-boot-server:
	cd backend && MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" mvn spring-boot:run

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
	kubectl label namespace default istio-injection=enabled
	@echo "Istio installation complete."
	rm -rf istio-1.18.2

# Updated target to include Istio installation
start-k3d-cluster-with-istio: create-k3d-registry create-k3d-cluster install-istio