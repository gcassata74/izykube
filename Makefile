# IzyKube
# Copyright (c) 2026-present Izylife Solutions s.r.l.
# Author: Giuseppe Cassata
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.

SHELL := /bin/bash

TF ?= terraform
CLUSTER_ROOT := terraform/cluster
ADDONS_ROOT := terraform/addons
KUBECONFIG := .izykube/config
HOST_KUBECONFIG := .izykube/host-config
K3S_CONTAINER ?= izykube-k3s
LEGACY_CONTAINERS ?= k3d-izyregistry
TF_PLAN_ARGS ?=
TF_APPLY_ARGS ?=
TF_DESTROY_ARGS ?=

.PHONY: init plan apply destroy app cleanup-docker wait-k3s check-kubeconfig

init:
	$(TF) -chdir=$(CLUSTER_ROOT) init
	$(TF) -chdir=$(ADDONS_ROOT) init

check-kubeconfig:
	@test -s "$(KUBECONFIG)" && test -s "$(HOST_KUBECONFIG)" || \
		{ echo "ERROR: k3s kubeconfigs are missing; run 'make apply' for the cluster first." >&2; exit 1; }

wait-k3s:
	@timeout 300 bash -c '\
		until [[ "$$(docker inspect -f '\''{{.State.Health.Status}}'\'' $(K3S_CONTAINER) 2>/dev/null)" == healthy ]] && \
		      [[ -s "$(KUBECONFIG)" ]] && [[ -s "$(HOST_KUBECONFIG)" ]]; do \
			status="$$(docker inspect -f '\''{{.State.Health.Status}}'\'' $(K3S_CONTAINER) 2>/dev/null || true)"; \
			if [[ "$$status" == unhealthy ]]; then \
				docker logs --tail 100 $(K3S_CONTAINER) >&2; exit 1; \
			fi; \
			sleep 2; \
		done'

plan: init
	$(TF) -chdir=$(CLUSTER_ROOT) plan $(TF_PLAN_ARGS)
	$(MAKE) check-kubeconfig
	$(TF) -chdir=$(ADDONS_ROOT) plan $(TF_PLAN_ARGS)

apply: init
	$(TF) -chdir=$(CLUSTER_ROOT) apply $(TF_APPLY_ARGS)
	$(MAKE) wait-k3s
	$(TF) -chdir=$(ADDONS_ROOT) apply $(TF_APPLY_ARGS)

app: wait-k3s
	@if docker ps --format '{{.Names}}' | rg -qx 'mongo-host'; then \
		echo "Stopping mongo-host to release port 27017 (volumes are preserved)..."; \
		docker stop mongo-host; \
	fi
	docker compose up -d --build mongo ollama izykube

cleanup-docker:
	docker compose down --remove-orphans
	@for name in $(LEGACY_CONTAINERS); do \
		if docker container inspect "$$name" >/dev/null 2>&1; then \
			echo "Removing legacy container $$name..."; \
			docker container rm -f "$$name"; \
		fi; \
	done

destroy: init cleanup-docker
	$(TF) -chdir=$(ADDONS_ROOT) destroy $(TF_DESTROY_ARGS)
	$(TF) -chdir=$(CLUSTER_ROOT) destroy $(TF_DESTROY_ARGS)
