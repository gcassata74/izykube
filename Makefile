# Makefile

# Define default shell to be used
SHELL := /bin/bash

run-chrome-dev:
	google-chrome --incognito --new-window "http://127.0.0.1:4200" --remote-debugging-port=9222 --disable-web-security --user-data-dir="~/ChromeDevSession"

run-angular-client:
	cd frontend && npm start

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

create-k3d-clusterDTO:
	k3d clusterDTO create -p "9000:80@loadbalancer" --registry-use k3d-test-app-registry:5000

delete-k3d-clusterDTO:
	k3d clusterDTO delete

start-k3d-clusterDTO: create-k3d-registry create-k3d-clusterDTO

restart-k3d-clusterDTO: delete-k3d-clusterDTO create-k3d-registry create-k3d-clusterDTO