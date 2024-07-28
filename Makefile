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
	google-chrome --incognito --new-window "http://127.0.0.1:4200" --remote-debugging-port=9222 --disable-web-security --user-data-dir="~/ChromeDevSession"

run-angular-client:
	cd frontend  && npm start

run-spring-boot-server:
	cd backend && MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" mvn spring-boot:run

create-docker-registry:
	docker run -d --name k3d-test-app-registry -p 5000:5000 --restart=always registry:2

create-k3d-registry:
	k3d registry create test-app-registry --port 5000

delete-docker-registry:
	docker stop k3d-test-app-registry && docker rm -v k3d-test-app-registry

delete-k3d-registry:
	k3d registry delete test-app-registry

create-k3d-cluster:
	k3d cluster create -p "9000:80@loadbalancer" --registry-use k3d-test-app-registry:5000

delete-k3d-cluster:
	k3d cluster delete

start-k3d-cluster: create-k3d-registry create-k3d-cluster

restart-k3d-cluster: delete-k3d-cluster create-k3d-registry create-k3d-cluster
