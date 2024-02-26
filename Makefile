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

create-k3d-cluster: create-k3d-registry
	k3d cluster create -p "9000:80@loadbalancer" --registry-use k3d-test-app-registry:5000

delete-k3d-cluster:
	k3d cluster delete

start-k3d-cluster: create-k3d-registry create-k3d-cluster

restart-k3d-cluster: delete-k3d-cluster create-k3d-registry create-k3d-cluster


build-and-register-image:
	@echo "Building Docker image from directory: $(DIR) with image name: $(IMG_NAME)"
	# Build the Docker image and tag it
	docker build $(DIR) -t $(IMG_NAME):latest
	# Tag the image for the local registry
	docker tag $(IMG_NAME):latest localhost:5000/$(IMG_NAME):latest
	# Push the image to the local registry
	docker push localhost:5000/$(IMG_NAME):latest
	# Call the asset API to register the image in the asset collection
	curl -X POST -H "Content-Type: application/json" \
	-d '{ \
		"id": "$(ID)", \
		"name": "$(NAME)", \
		"description": "$(DESCRIPTION)", \
		"port": $(PORT), \
		"image": "localhost:5000/$(IMG_NAME):latest", \
		"version": "latest" \
	}' \
	http://localhost:8090/api/asset

# Usage: make build-and-register-image DIR=./path/to/dockerfile-directory IMG_NAME=custom-image-name ID=asset-id NAME=asset-name DESCRIPTION="asset description" PORT=1234