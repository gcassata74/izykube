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

## Kubernetes Bootstrap (k3d)

```bash
make create-k3d-registry
make create-k3d-cluster
```

or:

```bash
make start-k3d-cluster
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

| Command                             | Description                                                             |
|-------------------------------------|-------------------------------------------------------------------------|
| `make run-spring-boot-server`       | Build and start backend jar with debug port                             |
| `make run-angular-client`           | Start Angular dev server                                                 |
| `make run-chrome-dev`               | Open Chrome dev profile                                                  |
| `make create-k3d-registry`          | Create k3d registry                                                      |
| `make create-k3d-cluster`           | Create k3d cluster                                                       |
| `make start-k3d-cluster`            | Create registry and cluster                                              |
| `make restart-k3d-cluster`          | Recreate k3d cluster                                                     |
| `make install-istio`                | Install Istio                                                            |
| `make start-k3d-cluster-with-istio` | Create cluster and install Istio                                         |
| `make install-cluster-addons`       | Install OLM, cert-manager, Istio gateway, Prometheus, Grafana, Ollama   |
| `make grafana-port-forward`         | Start Grafana port-forward on `localhost:3000`                          |
| `make run-i18n-extract`             | Extract i18n messages                                                    |
| `make run-i18n-build LOCALE=xx`     | Build with locale                                                        |
| `make run-i18n-serve LOCALE=xx`     | Serve with locale                                                        |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

For code changes, use small PRs with:

- clear problem statement
- scope-limited diff
- build/test evidence

## License

This project is licensed under the GNU Affero General Public License v3.0 or later (AGPL-3.0-or-later). See [LICENSE.md](LICENSE.md).
