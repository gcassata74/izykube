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

For the proposed STARK and AIssure work, the target product boundary is a headless Kubernetes policy, verified workload-identity, admission-enforcement, and assurance-evidence layer. Vulnerability and software-supply-chain scanning remains NocScan's responsibility; IzyKube will consume authenticated, digest-bound findings rather than duplicate the scanner. This is a roadmap boundary, not a claim that those capabilities are implemented or contractually assigned. The [requirements traceability matrix](docs/eu/requirements-traceability.md) records the provisional mappings, proposed KPIs, missing authoritative sources, and approval gates.

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
- `terraform/`: Docker/k3s infrastructure and Kubernetes addon Terraform roots
- `yaml/`: Kubernetes manifests used by setup flows
- `docs/`: project documentation
- `Dockerfile`: multi-stage build — Maven compiles frontend + backend into a single self-contained jar; JRE runtime image
- `docker-compose.yml`: MongoDB, Ollama, and IzyKube application services on the Terraform-managed network
- `.github/workflows/`: CI and release pipelines


## Prerequisites

- Java 21 (for backend development and Maven builds)
- Node.js 18+ / npm (for frontend development and builds)
- Docker (with Compose v2)
- kubectl
- helm
- `timeout` and `rg` (used by the Makefile)
- Terraform 1.6+ (providers are downloaded during init)

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

## Local Development

### Start the local stack

```bash
make init
make apply
make app
```

`make apply` provisions the local registry and single-node k3s cluster, waits for the k3s health probe and generated kubeconfigs, and then applies the Kubernetes addons. `make app` starts MongoDB, Ollama, and IzyKube on the shared Docker network. The app is exposed on http://localhost:8090.

The first run should use `make apply` before `make plan`, because the addons root needs the host-side kubeconfig generated by the cluster root. On an existing cluster, `make plan` plans both roots.

The two generated kubeconfigs have different purposes:

- `.izykube/host-config`: use this from the host for `kubectl` and the addons Terraform root;
- `.izykube/config`: use this inside the IzyKube container, where the Kubernetes API is reachable as `https://k3s:6443`.

For example:

```bash
kubectl --kubeconfig .izykube/host-config get nodes
```

### Start frontend in dev mode (hot-reload)

```bash
npm -C frontend start
```

### Optional: open a dev browser profile

```bash
google-chrome --new-window http://localhost:4200
```

## Build Commands

### Frontend only

```bash
npm -C frontend install --legacy-peer-deps
npm -C frontend run build -- --configuration production
```

## Terraform infrastructure and Make targets

The infrastructure is managed from two Terraform roots under `terraform/`. The Makefile runs them in the required order: cluster first, addons second.

### 1) Clone and bootstrap

```bash
git clone https://github.com/izylife/izykube.git
cd izykube
make init
make apply
make app
```

This provisions the local registry, k3s, OLM/operators, cert-manager and the internal CA, Istio ingress, Prometheus, and Grafana. Both kubeconfigs are generated under `.izykube/`.

### 2) Plan changes

```bash
make plan
```

This plans the cluster root and then the addons root. The cluster must already have been applied so that `.izykube/host-config` exists.

### 3) Local CA trust (optional)

The CA certificate is stored in the cert-manager/izykube-ca Secret. Export or install it using your platform trust-store tooling when HTTPS browser trust is needed.

### 4) Teardown

`make destroy` first stops Compose services and removes the legacy registry container, then destroys addons before the cluster. Compose volumes are preserved.

```bash
make destroy
```

Use `TF_APPLY_ARGS` and `TF_DESTROY_ARGS` to pass Terraform flags:

```bash
make apply TF_APPLY_ARGS=-auto-approve
make destroy TF_DESTROY_ARGS=-auto-approve
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

### Grafana port-forward

```bash
kubectl --kubeconfig .izykube/host-config -n istio-system-db port-forward svc/grafana 3000:80
```

This forwards Grafana to http://localhost:3000.

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

| Target | What it does |
|---|---|---|
| `make init` | Initializes both Terraform roots and downloads providers. |
| `make plan` | Plans cluster and addons; requires an existing generated kubeconfig. |
| `make apply` | Applies cluster, waits for k3s readiness, then applies addons. |
| `make app` | Builds and starts MongoDB, Ollama, and IzyKube with Docker Compose. |
| `make destroy` | Stops Compose, removes legacy Docker resources, then destroys addons and cluster. |

The roots can also be run directly when needed:

| Root | Scope | Main command |
|---|---|---|
| `terraform/cluster` | Docker network, registry, k3s, persistent volumes, kubeconfig | `terraform -chdir=terraform/cluster apply` |
| `terraform/addons` | OLM/operators, cert-manager/CA, Istio ingress, Prometheus, Grafana | `terraform -chdir=terraform/addons apply` |

Frontend development uses the native npm commands documented above. Terraform state contains the generated CA private key, so use protected remote state for shared or production environments.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

For code changes, use small PRs with:

- clear problem statement
- scope-limited diff
- build/test evidence

## License

This project is licensed under the GNU Affero General Public License v3.0 or later (AGPL-3.0-or-later). See [LICENSE.md](LICENSE.md).
