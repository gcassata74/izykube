<!--
  IzyKube
  Copyright (c) 2026-present Izylife Solutions s.r.l.
  Author: Giuseppe Cassata

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License as published
  by the Free Software Foundation, either version 3 of the License,
  or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU Affero General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program. If not, see <https://www.gnu.org/licenses/>.
-->

# IzyKube

IzyKube is a Kubernetes architecture designer for modeling Kubernetes namespaces, generating manifests, and applying them to a target cluster.
The UI is diagram-based and uses `interact.js` + SVG for node/link editing.

![IzyKube demo](docs/demo.png)

## Scope

IzyKube supports:

- namespace modeling with version snapshots
- visual editing of workloads and related resources
- template generation and deployment/undeployment
- runtime inspection from Kubernetes
- optional YAML import/export and Helm export

## Technology Stack

- Frontend: Angular
- Backend: Spring Boot
- Persistence: MongoDB
- Cluster API: Kubernetes (Fabric8 client)
- Diagram interactions: interact.js

## Repository Layout

- `frontend/`: Angular client
- `backend/`: Spring Boot API and orchestration logic
- `yaml/`: Kubernetes manifests used by setup flows
- `docs/`: project documentation
- `Makefile`: local development and cluster bootstrap commands

## Prerequisites

- Java 21
- Node.js 18+ (20 recommended)
- npm
- Docker
- kubectl
- helm
- k3d
- openssl
- Kubernetes cluster access (k3d/minikube/remote)

## Local Development

### Start backend

```bash
make run-spring-boot-server
```

### Start frontend

```bash
make run-angular-client
```

### Optional: open a dev browser profile

```bash
make run-chrome-dev
```

## Build Commands

### Backend

```bash
./mvnw -pl backend -am -DskipTests clean package
```

### Frontend

```bash
npm -C frontend install --legacy-peer-deps
npm -C frontend run build -- --configuration production
```

## Install From Scratch (k3d, zero to running)

### 1) Clone and install frontend dependencies

```bash
git clone https://github.com/izylife/izykube.git
cd izykube
npm -C frontend install --legacy-peer-deps
```

### 2) Create local k3d cluster

`make` targets are idempotent for registry/cluster creation, so re-running is safe.

```bash
make create-k3d-cluster
```

### 3) Install all cluster addons

This installs OLM, cert-manager, internal CA in cluster, Istio + gateway, Prometheus, Grafana, and Ollama.

```bash
make install-cluster-addons
```

### 4) Trust internal CA on your local machine (recommended for HTTPS routes)

Run in an interactive terminal (sudo password may be required):

```bash
sudo -v && make install-ca-local
```

### 5) Start backend and frontend

Terminal 1:

```bash
make run-spring-boot-server
```

Terminal 2:

```bash
make run-angular-client
```

### 6) Optional: open dedicated browser profile

```bash
make run-chrome-dev
```

## Kubernetes Bootstrap (k3d)

```bash
make create-k3d-registry
make create-k3d-cluster
```

with Istio:

```bash
make start-k3d-cluster-with-istio
```

cleanup:

```bash
make delete-k3d-cluster
make delete-k3d-registry
```

## Optional Services

### Ollama (local AI integration)

```bash
docker compose -f docker-compose.ollama.yaml up -d
docker compose -f docker-compose.ollama.yaml ps
```

Default backend settings are in:

- `backend/src/main/resources/application.yaml`

### Grafana port-forward from Makefile

```bash
make grafana-port-forward
```

This forwards Grafana to `http://localhost:3000`.

## Functional Notes

### Diagram link conventions

- `Ingress -> Service -> Deployment`
- `Service -> Deployment`
- `ConfigMap/Secret/Volume -> Deployment`
- `Job -> Deployment` or `Job -> Container` (when modeled that way)

### Namespace versions

- versions are stored and listed per namespace
- each row can be opened in diagram view
- rows can be deleted from the versions grid

## Makefile Reference

Main tasks with target dependencies and behavior:

| Command | Depends on (Make targets) | What it does |
|---|---|---|
| `make run-spring-boot-server` | `-` | Builds backend with Maven and starts Spring Boot jar with JDWP debug on port `5005`. |
| `make run-angular-client` | `-` | Kills process on port `4200` (if any) and starts Angular dev server from `frontend/`. |
| `make run-chrome-dev` | `-` | Opens Chrome with a dedicated insecure dev profile for local UI testing. |
| `make create-k3d-registry` | `-` | Creates k3d local registry `izyregistry` on port `5000` if it does not already exist. |
| `make create-k3d-cluster` | `create-k3d-registry` | Creates k3d cluster `izycluster` only if missing, attaches local registry, exposes `80/443`, disables Traefik. |
| `make restart-k3d-cluster` | `delete-k3d-cluster`, `create-k3d-cluster` | Recreates cluster from scratch. |
| `make install-istio` | `-` | Installs Istio (downloads `istioctl` if missing), enables sidecar injection on `default` namespace. |
| `make start-k3d-cluster-with-istio` | `create-k3d-cluster`, `install-istio` | Creates cluster and installs Istio in one command. |
| `make create-internal-ca` | `install-cert-manager` | Generates internal CA cert/key, creates `izykube-ca` TLS secret, applies ClusterIssuer manifest. |
| `make install-ca-local` | `create-internal-ca` | Installs cluster CA certificate in local OS trust store (`/usr/local/share/ca-certificates`). |
| `make grafana-port-forward` | `install-grafana-release` | Starts background port-forward to Grafana service on `http://localhost:3000`. |
| `make install-cluster-addons` | `install-olm`, `create-internal-ca`, `install-istio-gateway`, `install-prometheus`, `install-grafana`, `install-ollama` | Installs core platform addons: OLM, cert-manager/CA, Istio gateway, Prometheus, Grafana, Ollama. |
| `make run-i18n-extract` | `-` | Extracts Angular i18n messages into `frontend/src/locale`. |
| `make run-i18n-build LOCALE=xx` | `-` | Builds Angular app using the selected i18n configuration (`LOCALE`, default `en`). |
| `make run-i18n-serve LOCALE=xx` | `-` | Serves Angular app using the selected i18n configuration (`LOCALE`, default `en`). |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

For code changes, use small PRs with:

- clear problem statement
- scope-limited diff
- build/test evidence

## License

This project is licensed under the GNU Affero General Public License v3.0 or later (AGPL-3.0-or-later). See [LICENSE.md](LICENSE.md).
