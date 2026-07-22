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

IzyKube is a self-hosted Kubernetes architecture designer. Its tested paths cover visual namespace modeling plus YAML and Helm generation for supported resource types. The repository also contains cluster apply/delete, inspection, and local-AI integration paths whose end-to-end coverage is still partial.

The UI is diagram-based and uses `interact.js` + SVG for node/link editing. See the [capability evidence matrix](docs/product/capability-evidence-matrix.md) for code references, verification commands, and status definitions, and the [background-IP engineering record](docs/product/background-ip.md) for the repository-history cutoff and limitations.

![IzyKube demo](docs/demo.png)

## Current, Partial, External, and Roadmap Scope

### Verified current capabilities

- visual editing of Kubernetes workloads and related resources;
- YAML import/export and Helm chart export for the supported model types;
- Kubernetes RBAC policy modeling and manifest generation; and
- Istio VirtualService modeling and manifest export.

“Verified” is limited to the automated tests cited in the evidence matrix; it is not a production-readiness or completeness claim.

### Partially verified capabilities

- namespace version records can be saved, listed, opened, and deleted, but a tested one-click rollback flow is not established;
- apply/undeploy code paths use a connected Kubernetes API, but the repository has no automated live-cluster end-to-end test;
- runtime resource inspection and sync indicators exist for implemented views, but comprehensive drift detection is not established; and
- the local-AI adapter calls Ollama for generation/chat, but no automated Ollama contract or model-quality test is present.

### Externally supplied runtime components

The repository can orchestrate or connect to k3s, Istio, cert-manager, OLM, Prometheus, Grafana, and Ollama. These are third-party dependencies, not IzyKube-owned implementations. MongoDB, Kubernetes, Docker, Helm, Fabric8, Angular, Spring, and the selected AI models are external as well.

### Roadmap only

OPA/Rego, SPIFFE/SPIRE, Model Context Protocol (MCP), OpenTelemetry, first-party SBOM publication, and Sigstore/SLSA signing or provenance are not implemented capabilities. They remain roadmap candidates unless and until code plus reproducible verification is added.

## Technology Stack

- Frontend: Angular
- Backend: Spring Boot
- Persistence: MongoDB
- Cluster API: Kubernetes (Fabric8 client)
- Diagram interactions: interact.js

## Repository Layout

- `frontend/`: Angular client
- `backend/`: Spring Boot API and orchestration logic
- `installer/`: standalone Python/Tkinter GUI installer that drives Compose and Makefile tasks from outside the Java app
- `yaml/`: Kubernetes manifests used by setup flows
- `docs/`: project documentation
- `Dockerfile`: multi-stage build — Maven compiles frontend + backend into a single self-contained jar; JRE runtime image
- `docker-compose.yml`: configured local services (MongoDB, Docker registry, k3s, Ollama, IzyKube)
- `.github/workflows/`: CI and release pipelines
- `Makefile`: cluster addon bootstrap commands

## Prerequisites

- Java 21 (for backend development and Maven builds)
- Node.js 18+ / npm (for frontend development and builds)
- Docker (with Compose v2)
- kubectl
- helm
- openssl

## Docker Image

Pre-built images are published to DockerHub at [`gcassata/izykube`](https://hub.docker.com/r/gcassata/izykube):

| Tag | Published by |
|---|---|
| `latest` | Every merge to `main` (after CI passes) |
| `<sha>` | Same push, short commit SHA |
| `1.2.3`, `1.2` | Every `v*` tag / GitHub release |

To run without building locally:

```bash
docker pull gcassata/izykube:latest
```

## Standalone Installer

The graphical installer lives in [`installer/README.md`](installer/README.md) and is intentionally separate from the Java backend and Angular frontend.

It is packaged with PyInstaller and includes Python/Tcl/Tk inside the executable, so the target machine only needs Docker Engine and Docker Compose.

Typical entry point:

```bash
make setup-gui-build
./dist/IzyKubeSetup
```

## Local Development

### Start the configured Compose services

```bash
docker compose up
```

This builds the IzyKube image and starts the app plus the configured third-party MongoDB, registry, k3s, and Ollama services on the local Compose network. The app is exposed on `http://localhost:8090`.

### Start frontend in dev mode (hot-reload)

```bash
make run-angular-client
```

### Optional: open a dev browser profile

```bash
make run-chrome-dev
```

## Build Commands

### Frontend only

```bash
npm -C frontend install --legacy-peer-deps
npm -C frontend run build -- --configuration production
```

## Manual Installation Path

If you want the manual route, the same stack can be started directly with Compose. For the graphical route, use the standalone installer above.

### 1) Clone

```bash
git clone https://github.com/izylife/izykube.git
cd izykube
```

### 2) Start the stack

```bash
docker compose up
```

MongoDB, registry, k3s, Ollama, and izykube start together. The first run builds the image (Maven + frontend).

### 3) Install cluster addons into k3s

Once k3s is healthy (`docker compose ps` shows `izykube-k3s` healthy), point kubectl at the compose kubeconfig and install addons:

```bash
export KUBECONFIG=$(docker volume inspect izykube_kubeconfig --format '{{.Mountpoint}}')/config
make install-cluster-addons
```

This runs repository orchestration that installs the third-party OLM, cert-manager, Istio, Prometheus, and Grafana components plus repository-supplied CA/Gateway configuration.

### 4) Trust internal CA on your local machine (recommended for HTTPS routes)

Run in an interactive terminal (sudo password may be required):

```bash
sudo -v && make install-ca-local
```

### 5) Optional: open dedicated browser profile

```bash
make run-chrome-dev
```

## CI/CD

### CI (`ci.yml`) — triggers on pull request and push to `main`

| Job | What it does |
|---|---|
| `backend` | Runs tests, packages jar, uploads artifact |
| `frontend` | Installs deps, runs headless unit tests, production build, uploads dist |
| `quality-gates` | Checks no hardcoded warn toasts, no `stringData` in manifest generator |
| `docker-publish` | Builds Docker image and pushes `gcassata/izykube:latest` + `:<sha>` — only on push to `main`, after all three jobs pass |

### Release (`release.yml`) — triggers on `v*` tags

Builds backend + frontend, creates a GitHub release with jar / frontend tarball / SHA256 checksums, then builds and pushes `gcassata/izykube:<version>`, `:<major.minor>`, and `:latest` to DockerHub.

### GitHub environment

Both Docker publish jobs use the `izykube` GitHub environment. Required secrets:

| Secret | Value |
|---|---|
| `DOCKERHUB_USERNAME` | `gcassata` |
| `DOCKERHUB_TOKEN` | DockerHub personal access token |

## Optional Services

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
| `make setup-gui` | `-` | Starts the standalone installer in development mode from the `installer/` directory. |
| `make setup-gui-build` | `-` | Builds the standalone installer executable with PyInstaller inside Docker. |
| `make run-angular-client` | `-` | Kills process on port `4200` (if any) and starts Angular dev server from `frontend/`. |
| `make run-chrome-dev` | `-` | Opens Chrome with a dedicated insecure dev profile for local UI testing. |
| `make install-istio` | `-` | Installs Istio (downloads `istioctl` if missing), enables sidecar injection on `default` namespace. |
| `make create-internal-ca` | `install-cert-manager` | Generates internal CA cert/key, creates `izykube-ca` TLS secret, applies ClusterIssuer manifest. |
| `make install-ca-local` | `create-internal-ca` | Installs cluster CA certificate in local OS trust store (`/usr/local/share/ca-certificates`). |
| `make grafana-port-forward` | `install-grafana-release` | Starts background port-forward to Grafana service on `http://localhost:3000`. |
| `make install-cluster-addons` | `install-olm`, `create-internal-ca`, `install-istio-gateway`, `install-prometheus`, `install-grafana` | Installs core platform addons: OLM, cert-manager/CA, Istio gateway, Prometheus, Grafana. |
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
