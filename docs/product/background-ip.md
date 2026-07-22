# IzyKube background-IP engineering record

## Purpose and limits

This document records which technical assets can be demonstrated in this Git repository before the capability-audit issue was opened. It is an evidence index, not a legal opinion. Authorship, employee/contractor assignments, third-party license compliance, funding conditions, and eligibility as background IP all **require legal validation**.

## Cutoff

- **Engineering cutoff date:** 2026-07-12
- **Cutoff commit:** `fc1ded78b8a7f0b54118229a128a871531268052` (`Fix installer uninstall flow`)
- **Reason:** this is the tip of `main` immediately before issue [#3](https://github.com/gcassata74/izykube/issues/3) was opened on 2026-07-22.
- **Historical tag:** `backup-main-before-reset` points to `35b25db9cea8fa476546066ff32119bd434702ee` (`Initial commit`, 2022-07-27). It proves an early repository state, not the later capabilities.

No European call, proposal, grant-agreement, or submission date is identified in the repository. Therefore, whether 2026-07-12 is the correct legal “before the European calls” boundary **requires legal validation**. If a binding date is supplied, rerun this inventory against the last commit strictly before that date.

Reproduce the cutoff evidence:

```bash
git show --no-patch --format=fuller fc1ded78b8a7f0b54118229a128a871531268052
git tag --contains 35b25db9cea8fa476546066ff32119bd434702ee
git ls-tree -r --name-only fc1ded78b8a7f0b54118229a128a871531268052
```

## Demonstrable pre-cutoff assets

The following items exist at the cutoff commit. Their detailed implementation and verification references are in the [capability evidence matrix](capability-evidence-matrix.md).

| Asset at cutoff | Repository evidence | History anchor | Classification note |
|---|---|---|---|
| Angular visual namespace editor and diagram model | `frontend/src/app/diagram/`, cluster forms, frontend models and services | `a4ef88f4fd43e98cf5131bce7492e34f73953851` (2024-01-23) records the current diagram component path | Repository application code; third-party Angular, PrimeNG, interact.js, and related code excluded. |
| Spring Boot API, persistence, and Kubernetes orchestration | `backend/src/main/java/com/izylife/izykube/` | `b13d962b3d5b4b70d662c252dab0c0db036a25e7` (2024-10-05) records the current template-service path | Repository application/orchestration code; Spring, MongoDB, Fabric8, and Kubernetes excluded. |
| YAML import/export and Helm archive generation | `ClusterYamlService`, `HelmChartArchive`, associated tests | `5d0f46fbaffa13e712de3329f49879a50a322481` (2025-11-15) records the current YAML service path | Repository transformation code; Kubernetes/Helm specifications and libraries excluded. |
| RBAC policy planning and manifest generation | `RbacProcessor`, `RbacPlanner`, access-policy UI, associated tests | `0aa7cbb590a64fd109ee92cf81b99af1e155300a` (2025-12-26) records the current processor path | Repository planning/generation code; Kubernetes RBAC enforcement excluded. |
| Istio resource modeling and inspection integration | `VirtualServiceProcessor`, Istio form/model, explorer integration | `d0717a2d72110dc46f1a371605721b78646c9717` (2025-11-25) records the current processor path | Integration code only; Istio runtime, CRDs, and Fabric8 Istio models excluded. |
| Kubernetes resource explorer and operational UI | `KubernetesExplorerService`, explorer UI/services | `52c16a7e2953d16bd24bccd08c302073bf6bc3af` (2025-10-17) records the current explorer-service path | Repository integration/UI code; live cluster behavior is externally supplied. |
| Local-AI REST adapter and assistant client | `LocalAiService`, `AiController`, `ai-assistant.service.ts` | `943d08077b66bf653ab22f14c43a95fa84d8a602` (2025-10-18) records the current adapter path | Adapter/client code only; Ollama, models, weights, training data, and outputs excluded. |
| Compose/Makefile setup orchestration | `docker-compose.yml`, `Makefile`, `yaml/` | `91274b13dddb8a6763bcd38b7d5bcab9d6a03123` (2026-05-11) records the Compose file; the Makefile path exists by 2024-01-23 | Repository configuration/orchestration; installed products remain third-party dependencies. |
| Standalone Python/Tkinter installer | `installer/` and installer tests | `ce8683d227ad9000267583c7a55aade4d2f5c1e7` (2026-07-12) records the current entry-point path | Repository installer code; Python/Tk, ttkbootstrap, Docker, and packaged tools excluded. |

History anchors show when the current file paths first appear in reachable history. They are not necessarily the first conception of a feature, and commit subjects are not relied on as proof of technical behavior.

## Not demonstrated as pre-cutoff IzyKube capabilities

No first-party implementation was found at the cutoff for:

- OPA/Rego policy evaluation;
- SPIFFE/SPIRE workload identity;
- Model Context Protocol (MCP);
- OpenTelemetry instrumentation or collection;
- first-party SBOM generation/publication; or
- Sigstore signing or SLSA provenance.

These items are roadmap only. A transitive package, external installer target, product idea, or documentation mention is not treated as implementation evidence.

## Third-party and external components

k3s, Kubernetes, Istio, cert-manager, Prometheus, Grafana, OLM, Ollama, MongoDB, Docker, Helm, kubectl, OpenSSL, Java/Spring, Angular, Python/Tk, and their related libraries or images are external dependencies. IzyKube may contain adapters, manifests, configuration, or orchestration for them; this document does not claim ownership of those components.

Before using this record in a proposal or agreement, legal review should confirm at least:

1. the applicable call/submission cutoff date;
2. contributor identity and assignment records;
3. third-party and model/data license obligations;
4. whether repository history matches the relevant corporate asset records; and
5. the exact background/foreground definitions in the governing instrument.
