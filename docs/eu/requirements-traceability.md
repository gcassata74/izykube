# STARK and AIssure requirements traceability

Document status: **provisional and unapproved**<br>
Evidence cutoff: **2026-07-23**<br>
Owner: **IzyLife Solutions / IzyKube product owner**

This document is the requirements baseline for IzyKube's proposed STARK and AIssure work. It deliberately does not turn proposal ideas into contractual commitments. The final STARK and AIssure proposals, work-package descriptions, use cases, deliverables, and approved partner responsibilities were not available in the repository, connected Gmail, Google Drive, or Dropbox at the evidence cutoff. Consequently:

- no project-specific row below is classified as a verified mandatory commitment;
- stable IDs identify candidate requirements for discussion and future traceability, not accepted obligations;
- targets are proposed engineering targets until project governance approves them; and
- the matrix cannot be marked approved until the source and review gates in [Approval state](#approval-state) are complete.

Issue [#15](https://github.com/gcassata74/izykube/issues/15) requires unavailable material to be marked unverified rather than guessed. This draft follows that rule while giving later implementation issues stable anchors.

## Authority and classification rules

| Level | Source type | May establish a project commitment? | Treatment here |
|---|---|---:|---|
| A | Published call/topic text or signed grant agreement | Yes, at call/project level | Cite exactly; partner ownership still needs an approved allocation. |
| B | Submitted/approved proposal, annex, work package, use case, deliverable, or recorded governance decision | Yes | Required before a row becomes mandatory or IzyKube-owned. |
| C | Coordinator or consortium working concept | No | Candidate requirement only; mark unverified. |
| D | Partner capability worksheet or contribution proposal | No | Evidence of an offer or assumption, not acceptance. |
| E | Repository issue, audit, or roadmap | No | Engineering plan and current-status evidence only. |

Commitment classes used below are **mandatory**, **desirable**, **research hypothesis**, and **partner-owned**. Because no level-B allocation was available, every IzyKube row is currently **desirable / proposed** or **research hypothesis / proposed**. `Unverified` means a follow-up source or decision is named; it never means implemented.

## Source register

Private consortium attachments are identified for authorized reviewers but are not copied or linked from this public repository.

| ID | Source and version | Location used | Authority | Availability and limitation |
|---|---|---|---|---|
| `SRC-CALL-A1` | European Commission Funding & Tenders topic `HORIZON-CL3-2026-02-CS-ECCC-01`, current portal record | [Official topic record](https://ec.europa.eu/info/funding-tenders/opportunities/portal/screen/opportunities/topic-details/HORIZON-CL3-2026-02-CS-ECCC-01) | A | Confirms the call/topic context. The authoritative topic text must be archived with its publication/version before call requirements are copied into this matrix. |
| `SRC-STARK-C1` | `STARK_Consortium_Briefing.pdf`, July 2026, 11 pages | Gmail draft `STARK_Consortium_Briefing`, message `19f8b2150df6ea59`; pp. 2, 4–6, 8–9 | C | Prospective briefing that explicitly requires live Portal verification; not a final proposal or partner allocation. |
| `SRC-AI-C1` | `HORIZON-CL3-2026-02-CS-ECCC-01 Concept Note.pdf`, candidate-partner concept note, received 2026-06-03, 5 pages | Gmail message `19e8eb8ffe283f9d`; pp. 1–4 | C | Working AIssure concept; not a submitted/approved proposal. |
| `SRC-AI-C2` | `HORIZON-CL3-2026-02-CS-ECCC-01 Proposal Partener-capability-mapping.xlsx`, received 2026-06-03 | Gmail message `19e8eb8ffe283f9d`; `Capability mapping` rows 4–23 | C | Coordinator working matrix; technologies are stated as open to alternatives. |
| `SRC-AI-D1` | `worksheet_IzyLife.xlsx`, sent 2026-06-07 | Gmail message `19ea32230fd0a166`; `Capability mapping` rows 7–9, 17–18, 23 | D | IzyLife's proposed contribution and indicative effort; not evidence of acceptance. Capability claims are superseded by the repository audit where they conflict. |
| `SRC-NOC-D1` | `capability_mapping_cloudgsx (3).xlsx`, received 2026-06-07 | Gmail message `19ea3c9a4c972e41`; `Capability mapping` rows 4–6, 9, 13, 15, 17, 23 | D | CloudGSX's proposed NocScan contribution; supports the responsibility boundary but is not an approved allocation. |
| `SRC-AI-M1` | Email thread naming `aissure_agentic_supply-chains_v0_11.docx` and an Excel workbook in Teams, 2026-07-21–22 | Gmail thread `19f8a9d9a1a82a6f` | Metadata only | The named v0.11 proposal was not accessible in connected Gmail, Drive, Dropbox, or the repository. No requirements were extracted from it. |
| `SRC-AUDIT-E1` | [IzyKube capability evidence matrix](../product/capability-evidence-matrix.md), audit 2026-07-23 | Entire matrix | E | Governs all claims about implemented, partial, planned, and external capabilities. |
| `SRC-BOUNDARY-E1` | GitHub issue [#15](https://github.com/gcassata74/izykube/issues/15) | Scope, product-boundary and NocScan bullets | E | Repository working rule pending project-governance approval. |

### Missing authoritative sources

The following are required to promote candidate rows to verified requirements:

1. a versioned copy of the final official call/topic text;
2. the submitted/approved STARK proposal and IzyLife work-package, task, deliverable, milestone, and effort allocation;
3. the submitted/approved AIssure proposal (the email names v0.11 only as a current draft) and the same IzyLife allocation details;
4. approved use-case and pilot descriptions;
5. an approved IzyKube–NocScan responsibility matrix; and
6. a governance decision approving requirement ownership, KPI targets, evidence retention, and review deadlines.

## Product and responsibility boundary

The proposed IzyKube boundary is a **headless Kubernetes policy, verified workload-identity, admission-enforcement, and assurance-evidence layer**. It consumes normalized, source-authenticated, digest-bound security findings and software-supply-chain evidence. It makes and explains policy decisions, enforces them at approved control points, correlates telemetry, and produces independently verifiable receipts.

IzyKube is **not** a vulnerability, SAST, port, dependency, image, or malware scanner. NocScan, operated by CloudGSX, remains the proposed scanning provider. NocScan discovers vulnerabilities and produces scan/SBOM/risk outputs; IzyKube validates the source, schema, freshness, and artifact binding of those outputs and uses them as policy inputs. Issue [#19](https://github.com/gcassata74/izykube/issues/19) implements this adapter boundary.

For issue [#25](https://github.com/gcassata74/izykube/issues/25), generating an SBOM for IzyKube's own release artifacts and verifying supplied SBOM/provenance evidence does not authorize IzyKube to duplicate NocScan's scanning engine. Workload or project SBOM generation remains NocScan-owned unless governance explicitly changes the allocation.

The current product remains the audited Kubernetes architecture designer described in `SRC-AUDIT-E1`. OPA/Rego, SPIFFE/SPIRE, MCP, OpenTelemetry, first-party SBOM publication, and Sigstore/SLSA provenance are roadmap items, not current capabilities.

## Shared candidate requirements

All rows in this section are **unverified desirable capabilities** pending the missing level-B sources. Baselines and targets reference the KPI catalog below.

| ID | Source location and plain-English meaning | Current capability and gap | Implementation issue(s) | Verification | KPI and expected evidence |
|---|---|---|---|---|---|
| `IZY-SH-POL-001` | `SRC-STARK-C1` pp. 4–6 and `SRC-AI-C1` p. 3, P3: apply explainable, versioned policy to trusted inputs. | RBAC manifest planning is implemented; a decision contract and policy engine are absent. | [#17](https://github.com/gcassata74/izykube/issues/17), [#18](https://github.com/gcassata74/izykube/issues/18) | Schema fixtures, canonicalization tests, Rego unit tests, pinned-OPA adapter tests. | `KPI-POL-01`; schemas, policy catalog, bundle digest, allow/deny/indeterminate reports. |
| `IZY-SH-ID-001` | `SRC-STARK-C1` pp. 4–6 and `SRC-AI-C1` p. 3, P3: base authorization on a cryptographically verified workload identity and request context. | Kubernetes RBAC modeling exists; SPIFFE/SPIRE and verified request authorization are planned. | [#20](https://github.com/gcassata74/izykube/issues/20), [#21](https://github.com/gcassata74/izykube/issues/21) | Positive/negative SVID, trust-domain, rotation, replay, and context-authorization fixtures. | `KPI-ID-01`; identity conformance report and sanitized decision fixtures. |
| `IZY-SH-ADM-001` | `SRC-STARK-C1` pp. 5–6 and `SRC-AI-C1` pp. 3–4: enforce approved policy before protected Kubernetes changes take effect. | Apply/delete paths exist but no assurance admission webhook exists. | [#22](https://github.com/gcassata74/izykube/issues/22) | Disposable-cluster admission tests including timeout, bypass, unavailable dependency, and failure policy. | `KPI-ADM-01`; admission latency dataset, rejection fixtures, deployment manifest. |
| `IZY-SH-NOC-001` | `SRC-AI-D1` row 17, `SRC-NOC-D1` rows 4–6, 9, 13, 15, 17, and `SRC-BOUNDARY-E1`: consume scanner results without implementing scanning. | No NocScan adapter exists. Scanner ownership is proposed by CloudGSX and must be approved. | [#19](https://github.com/gcassata74/izykube/issues/19) | Offline contract fixtures and optional authorized live smoke test; wrong digest, stale, partial, failed, tampered, and unsupported inputs must fail distinctly. | `KPI-NOC-01`; responsibility matrix, schema mapping, sanitized fixtures, adapter report. |
| `IZY-SH-EVD-001` | `SRC-STARK-C1` pp. 4–5, 8–9 and `SRC-AI-C1` pp. 2–3, P2/P5: preserve decision inputs and outputs as tamper-evident assurance evidence. | No signed policy-decision receipt or assurance package exists. | [#23](https://github.com/gcassata74/izykube/issues/23) | Receipt canonicalization, signing, verification, tamper, key-rotation, and missing-evidence tests. | `KPI-EVD-01`; receipt schema, signatures, public verification instructions, sample evidence pack. |
| `IZY-SH-TEL-001` | `SRC-STARK-C1` pp. 5, 8–9 and `SRC-AI-C1` p. 3, P4: correlate policy, identity, external evidence, and enforcement events end to end. | Prometheus/Grafana setup is external; OpenTelemetry instrumentation is absent. | [#24](https://github.com/gcassata74/izykube/issues/24) | Trace propagation and metric tests across policy, NocScan, provenance, admission, and receipt paths; redaction checks. | `KPI-TEL-01`; trace samples, metric definitions, coverage and redaction report. |
| `IZY-SH-PROV-001` | `SRC-STARK-C1` pp. 5–6, 8 and `SRC-AI-C1` pp. 2–3, P2: bind software evidence to immutable artifacts and verify its producer and integrity. | Release checksums exist; SBOM publication, signature, and SLSA provenance are absent. | [#25](https://github.com/gcassata74/izykube/issues/25) | Verify signed provenance/SBOM against digest; reject tampered, unsigned, wrong-subject, and untrusted-builder fixtures. | `KPI-PROV-01`; release SBOM, provenance statement, signature and verifier report. |
| `IZY-SH-SEC-001` | `SRC-STARK-C1` p. 6 and `SRC-AI-C1` pp. 2–4: validate trust-critical paths against realistic abuse and failure cases. | No approved project threat model or integrated adversarial suite exists. | [#26](https://github.com/gcassata74/izykube/issues/26) | Threat-to-test mapping, abuse cases, dependency failure, bypass, replay, tamper, and resource-exhaustion tests. | `KPI-SEC-01`; threat model, machine-readable results, residual-risk register. |
| `IZY-SH-DEP-001` | `SRC-STARK-C1` pp. 8–9 and `SRC-AI-C1` pp. 3–4: provide a reproducible environment for review and pilot execution. | Local Compose/setup paths are partial or external; the target headless assurance stack is not packaged. | [#27](https://github.com/gcassata74/izykube/issues/27) | Clean install, upgrade, rollback, uninstall, restart, and offline-image verification on a supported Kubernetes version. | `KPI-DEP-01`; immutable manifest, install log, conformance report, runbook. |
| `IZY-SH-MON-001` | `SRC-BOUNDARY-E1` and the approved product direction in [#31](https://github.com/gcassata74/izykube/issues/31): expose assurance status without browser-based mutation. | The current UI is a Kubernetes editor; no assurance read model or monitoring console exists. | [#16](https://github.com/gcassata74/izykube/issues/16), [#31](https://github.com/gcassata74/izykube/issues/31), [#32](https://github.com/gcassata74/izykube/issues/32), [#33](https://github.com/gcassata74/izykube/issues/33) | Route/API inventory, read-only browser traffic, mutation-negative tests, accessibility and projection reconciliation tests. | `KPI-MON-01`; endpoint inventory, E2E/a11y reports, production-bundle analysis. |

## STARK-specific candidate requirements

These rows are derived only from the prospective STARK briefing and are therefore **unverified research hypotheses or desirable capabilities**, not contractual STARK requirements.

| ID | Source location and plain-English meaning | Current capability and gap | Implementation issue(s) | Verification | KPI and expected evidence |
|---|---|---|---|---|---|
| `IZY-ST-ZT-001` | `SRC-STARK-C1` pp. 4–6 and 8–9: demonstrate a cloud/edge Kubernetes trust chain combining identity, artifact evidence, policy, enforcement, and operational evidence. | Individual target components are all planned; no approved STARK pilot/use case or end-to-end chain exists. | [#28](https://github.com/gcassata74/izykube/issues/28) plus shared component issues | Run the approved positive and negative scenario on a clean cluster and independently verify the resulting receipts. | `KPI-ST-01`; scenario manifest, immutable inputs, traces, receipts, demo recording and reproduction log. |
| `IZY-ST-OPS-001` | `SRC-STARK-C1` p. 5 O5 and p. 9: measure operational fit and produce reviewable validation evidence. | No approved STARK KPI set, baseline, target, or buyer/reviewer acceptance method exists. | [#28](https://github.com/gcassata74/izykube/issues/28), [#30](https://github.com/gcassata74/izykube/issues/30) | Recalculate approved KPIs from archived raw data for a pinned release. | `KPI-HO-01`; raw KPI data, calculation scripts, environment and release digests, signed review record. |

## AIssure-specific candidate requirements

These rows are derived from an AIssure concept note and partner worksheets. They are **unverified research hypotheses or desirable capabilities** until the current proposal and approved allocation are received.

| ID | Source location and plain-English meaning | Current capability and gap | Implementation issue(s) | Verification | KPI and expected evidence |
|---|---|---|---|---|---|
| `IZY-AI-TOOL-001` | `SRC-AI-C1` pp. 2–3, P3 and `SRC-AI-D1` row 7: constrain agent/tool requests through verified identity, explicit context, and policy; MCP is a candidate transport, not a current feature. | Local-AI adapter is partial; MCP, workload identity, policy, and tool authorization are absent. | [#17](https://github.com/gcassata74/izykube/issues/17)–[#24](https://github.com/gcassata74/izykube/issues/24) | Authorized/unauthorized tool-call fixtures, delegation/replay tests, decision receipts, and trace correlation. | `KPI-AI-TOOL-01`; approved contract fixtures, traces, receipts and attack-case report. |
| `IZY-AI-FID-001` | `SRC-AI-C1` pp. 2–3, P4 and `SRC-AI-D1` rows 9 and 18: capture enough runtime context to detect missing or altered agent/tool/build events. | No MCP event model or OpenTelemetry instrumentation exists; anomaly detection ownership is unallocated. | [#24](https://github.com/gcassata74/izykube/issues/24), [#26](https://github.com/gcassata74/izykube/issues/26), [#29](https://github.com/gcassata74/izykube/issues/29) | Compare an approved event sequence with dropped, reordered, replayed, and altered variants. | `KPI-AI-FID-01`; event schema, sequence fixtures, detection report and correlated traces. |
| `IZY-AI-CASE-001` | `SRC-AI-C1` pp. 1–3, AssuranceOps concept: produce a bounded assurance case whose claims point to identity, scan, provenance, policy, and enforcement evidence. | No approved AIssure use case, assurance-case schema, or end-to-end evidence package exists. | [#29](https://github.com/gcassata74/izykube/issues/29) plus shared component issues | Run approved compliant and controlled negative variants and verify the package independently. | `KPI-AI-CASE-01`; claim/context/assumption model, signed package, verifier output and residual uncertainty. |

## Partner-owned and excluded requirements

| ID | Proposed owner and source | Boundary | IzyKube interaction | Verification / follow-up |
|---|---|---|---|---|
| `EXT-NOC-SCAN-001` | CloudGSX / NocScan; `SRC-NOC-D1` rows 4–6, 9, 13, 15, 17 | Vulnerability discovery, advisory correlation, dependency/image/project scanning, scanner risk scoring, and automated remediation are outside IzyKube. | Consume only authorized, schema-valid, fresh, digest-bound findings through [#19](https://github.com/gcassata74/izykube/issues/19). | Governance must approve the responsibility matrix and interfaces. No NocScan capability claim is accepted merely because it appears in a partner worksheet. |
| `EXT-NOC-SBOM-001` | CloudGSX / NocScan; `SRC-NOC-D1` rows 4–5 and 17 | NocScan proposes scanned-project SBOM/provenance production and the related scan intelligence. | IzyKube verifies evidence and may create SBOM/provenance for its own release under [#25](https://github.com/gcassata74/izykube/issues/25). | Resolve project-workload versus IzyKube-release scope in the approved allocation. |
| `EXT-AI-DETECT-001` | Unallocated; `SRC-AI-C1` p. 3, P4 | Transformer/GNN anomaly-detection research is not assigned to IzyKube by any available source. | IzyKube may emit normalized telemetry and consume an approved detector outcome. | Coordinator must name the owner, interface, dataset, evaluation method, and issue; currently **no implementation issue**. |
| `EXT-PQC-001` | Unallocated; `SRC-AI-C1` pp. 2–4, P7 | Post-quantum release/update research is not assigned to IzyKube by any available source. | No IzyKube commitment is inferred. | Coordinator must decide relevance and ownership; currently **no implementation issue**. |
| `EXT-REG-001` | Unallocated; `SRC-AI-C2` row 12 | Regulatory/Notified-Body mapping needs legal and conformity expertise and is not assigned to IzyKube. | IzyKube may export technical evidence in an approved format. | Project governance must identify the legal owner and applicable controls; currently **no implementation issue**. |

## KPI catalog

All targets below are **proposed engineering targets**, not contractual KPIs. Baseline `0` means the audited repository has no implementation capable of producing the measurement; `unmeasured` means a related path exists but no reproducible measurement was found.

| KPI | Unit | Baseline | Proposed target | Measurement procedure | Data source |
|---|---|---:|---:|---|---|
| `KPI-POL-01` Policy determinism and fixture conformance | % approved fixtures producing the expected canonical decision and digest | 0% | 100% | Run every approved fixture twice against the pinned schema, engine, and bundle; compare decision and digest. | Versioned fixture results, schema and bundle digests. |
| `KPI-ID-01` Identity enforcement conformance | % approved positive/negative identity cases handled as expected | 0% | 100%; 0 invalid identities accepted | Run valid, expired, wrong-domain, revoked, replayed, rotated, and context-mismatch cases. | Identity integration-test report and trust configuration digest. |
| `KPI-ADM-01` Admission correctness and added latency | correctness %, p50/p95 milliseconds | 0%; unmeasured | 100% approved decisions enforced; p95 ≤ 200 ms in the reference environment | Replay the approved corpus through the webhook after warm-up; compare outcome and measure webhook duration separately. | Admission test output, OpenTelemetry histogram, environment manifest. |
| `KPI-NOC-01` External-finding integrity handling | % boundary fixtures classified correctly | 0% | 100%; 0 stale/tampered/wrong-digest reports treated as clean | Execute clean, vulnerable, missing, stale, partial, failed, tampered, wrong-digest, unsupported, and oversized fixtures. | Adapter contract-test report and sanitized fixtures. |
| `KPI-EVD-01` Receipt verifiability and tamper detection | % receipts verified; % mutations detected | 0% | 100% valid verified; 100% defined mutations rejected | Verify a generated corpus independently, then mutate each protected field and signature. | Receipt corpus, verifier output, key and schema identifiers. |
| `KPI-TEL-01` End-to-end correlation coverage | % completed evaluations with one correlation ID across required stages | 0% | ≥ 99% in the reference load test; 100% secret-redaction cases pass | Run representative evaluations; query traces for input, identity, NocScan, provenance, policy, admission, and receipt spans; run redaction assertions. | Trace export, metric query, redaction-test report. |
| `KPI-PROV-01` Provenance verification | % approved artifact fixtures classified correctly | 0% | 100%; 0 tampered/wrong-subject/untrusted-builder artifacts accepted | Verify signatures, subject digest, builder identity, SBOM link, and freshness for each fixture. | Verifier report, signed provenance and SBOM samples. |
| `KPI-SEC-01` Threat-control verification | % high/critical threat cases with a passing mitigation test | 0% | 100%; 0 unaccepted critical residual risks | Execute the threat-derived suite against the pinned release and reconcile results with the threat register. | Machine-readable security report and approved residual-risk register. |
| `KPI-DEP-01` Deployment reproducibility | % clean supported environments completing install/demo/uninstall | 0% | 100% across the approved environment matrix | Provision fresh environments from pinned inputs; record install, smoke test, restart, rollback, and uninstall. | Environment manifests, logs, image/chart digests. |
| `KPI-MON-01` Monitoring-only UI conformance | mutation requests, reconciliation %, serious/critical accessibility findings | Unmeasured | 0 mutation requests; 100% fixture reconciliation; 0 serious/critical findings | Intercept browser traffic, compare rendered aggregates with fixtures, scan accessibility, and test former mutation routes. | E2E network log, reconciliation output, accessibility and bundle reports. |
| `KPI-ST-01` STARK scenario reproducibility | % approved scenario steps and expected decisions reproduced | 0% | 100% in two consecutive clean runs | Execute the governance-approved scenario and negative variants from immutable inputs; independently verify receipts. | Run logs, traces, receipts, cluster and release digests. |
| `KPI-AI-TOOL-01` Agent/tool authorization conformance | % approved request/delegation cases handled correctly | 0% | 100%; 0 unauthorized or replayed requests allowed | Run approved identity, scope, delegation, expiry, replay, and unavailable-dependency fixtures. | Contract-test output, decision receipts and traces. |
| `KPI-AI-FID-01` Runtime event-fidelity detection | % defined sequence corruptions detected | 0% | 100% of approved dropped/reordered/replayed/altered cases | Compare a reference sequence with one controlled corruption per run and calculate detections. | Event fixtures, detector/consumer output and traces. |
| `KPI-AI-CASE-01` Assurance-package completeness | % required evidence references present and independently valid | 0% | 100% for compliant case; every negative variant fails for the expected reason | Validate schema, references, digests, signatures, freshness, claim rule, and residual uncertainty with a separate verifier. | Signed package, verifier output and negative-case corpus. |
| `KPI-HO-01` Independent handover completion | % approved install/demo/diagnosis/uninstall steps completed without author assistance | 0% | 100% | A non-implementer follows only the pinned runbooks and records assistance, deviations, time, and result. | Signed handover checklist, logs and issue register. |

## Gap-to-issue map

| Gap | Requirement IDs | Issue(s) | Reverse-link state |
|---|---|---|---|
| Reproducible CI baseline | all | [#5](https://github.com/gcassata74/izykube/issues/5) | Requirement IDs not yet present in the issue; update after governance approves the matrix. |
| Headless product boundary and legacy removal | `IZY-SH-MON-001` | [#16](https://github.com/gcassata74/izykube/issues/16), [#31](https://github.com/gcassata74/izykube/issues/31) | Pending approval. |
| Decision contract and OPA | `IZY-SH-POL-001` | [#17](https://github.com/gcassata74/izykube/issues/17), [#18](https://github.com/gcassata74/izykube/issues/18) | Pending approval. |
| NocScan boundary/adapter | `IZY-SH-NOC-001`, `EXT-NOC-SCAN-001`, `EXT-NOC-SBOM-001` | [#19](https://github.com/gcassata74/izykube/issues/19) | Pending approval. |
| Workload identity and authorization | `IZY-SH-ID-001` | [#20](https://github.com/gcassata74/izykube/issues/20), [#21](https://github.com/gcassata74/izykube/issues/21) | Pending approval. |
| Admission enforcement | `IZY-SH-ADM-001` | [#22](https://github.com/gcassata74/izykube/issues/22) | Pending approval. |
| Receipts and telemetry | `IZY-SH-EVD-001`, `IZY-SH-TEL-001` | [#23](https://github.com/gcassata74/izykube/issues/23), [#24](https://github.com/gcassata74/izykube/issues/24) | Pending approval. |
| SBOM/provenance verification | `IZY-SH-PROV-001` | [#25](https://github.com/gcassata74/izykube/issues/25) | Pending approval; workload-generation boundary must be resolved. |
| Threat/security validation | `IZY-SH-SEC-001`, `IZY-AI-FID-001` | [#26](https://github.com/gcassata74/izykube/issues/26) | Pending approval. |
| Reproducible deployment | `IZY-SH-DEP-001` | [#27](https://github.com/gcassata74/izykube/issues/27) | Pending approval. |
| STARK demonstrator | `IZY-ST-ZT-001`, `IZY-ST-OPS-001` | [#28](https://github.com/gcassata74/izykube/issues/28) | Pending approval and approved use case. |
| AIssure demonstrator | `IZY-AI-TOOL-001`, `IZY-AI-FID-001`, `IZY-AI-CASE-001` | [#29](https://github.com/gcassata74/izykube/issues/29) | Pending approval and approved use case. |
| Monitoring read model and console | `IZY-SH-MON-001` | [#32](https://github.com/gcassata74/izykube/issues/32), [#33](https://github.com/gcassata74/izykube/issues/33) | Pending approval. |
| Integrated validation and handover | all approved IzyKube requirements | [#30](https://github.com/gcassata74/izykube/issues/30) | Pending approval. |
| AI anomaly detector | `EXT-AI-DETECT-001` | **No issue** | Owner and interface not allocated. |
| PQC release/update research | `EXT-PQC-001` | **No issue** | Owner and relevance not allocated. |
| Regulatory/conformity mapping | `EXT-REG-001` | **No issue** | Legal/technical owner not allocated. |

Reverse links are intentionally not written into implementation issues while the IDs remain provisional. After governance approval, the product owner must add the approved IDs and this document's permanent link to every mapped issue and run the check in [Verification procedure](#verification-procedure).

## Terminology

| Term | Required meaning in IzyKube |
|---|---|
| Policy | A versioned, reviewable rule set that evaluates canonical inputs and returns a normalized decision. It is not synonymous with Kubernetes RBAC or a UI form. |
| Identity | A verified cryptographic identity for a workload or authorized actor, including trust domain, validity, and relevant context; a caller-supplied name is not identity evidence. |
| Attestation | A signed statement by an identified issuer about a subject and predicate. It must name the subject digest and verification material. |
| Provenance | Verifiable metadata describing how, by whom/what, and from which inputs an artifact was produced. A checksum alone is not provenance. |
| Evidence | A referenced, integrity-protected artifact supporting an assurance claim or decision. Evidence may include attestations, findings, traces, tests, and receipts. |
| Risk | A scoped assessment derived from named inputs, method, time, and uncertainty. A vulnerability count or severity label alone is not the complete risk. |
| Admission | The Kubernetes API control point that accepts, rejects, or mutates a requested resource before persistence. The proposed IzyKube webhook validates; mutation is not assumed. |
| Enforcement | The act of making a policy outcome effective at a named control point. Evaluation without a protected control point is not enforcement. |
| Finding | A normalized observation from a named source. Missing, stale, partial, failed, or unavailable findings must not be represented as clean. |
| Receipt | A canonical, tamper-evident record of the decision inputs, policy and evidence references, outcome, time, and correlation identifiers. |
| Assurance case | A bounded claim with context, assumptions, decision rule, supporting evidence, and residual uncertainty; it is not a claim of certification. |

## Assumptions and open questions

Deadlines are decision targets for planning, not contractual dates.

| ID | Assumption or question | Owner | Decision deadline | Blocking effect / resolution evidence |
|---|---|---|---|---|
| `OQ-SRC-01` Obtain and archive the versioned official call/topic text. | Proposal coordinator | 2026-07-31 | Blocks verified call-level source references; archive URL, publication date, version, and checksum. |
| `OQ-ST-01` Which final STARK requirements, WPs, tasks, deliverables, milestones, pilots, effort, and KPIs are assigned to IzyLife? | STARK coordinator + IzyLife project lead | Before proposal submission | Blocks every STARK row from becoming mandatory/IzyKube-owned; supply approved proposal/allocation or signed decision. |
| `OQ-AI-01` Provide accessible `aissure_agentic_supply-chains_v0_11.docx` (or later authoritative version) and the current capability workbook. | AIssure coordinator / repository owner | 2026-07-31 | Blocks verification of AIssure requirements and source passages; grant access or attach versioned files. |
| `OQ-AI-02` Which AIssure requirements, pilots, effort, deliverables, and KPIs are accepted for IzyLife? | AIssure coordinator + IzyLife project lead | Before proposal submission | Blocks mandatory status and ownership; record approved allocation. |
| `OQ-NOC-01` Approve the IzyKube–NocScan responsibility and interface matrix, including SBOM/provenance scope. | CloudGSX + IzyLife technical leads + coordinator | 2026-08-07 | Blocks final `IZY-SH-NOC-001`/`IZY-SH-PROV-001` boundary; signed architecture/governance record. |
| `OQ-KPI-01` Approve or replace every proposed target and reference environment. | Project technical board | Before implementation acceptance | Blocks contractual KPI use; dated review record and frozen environment definition. |
| `OQ-OWN-01` Allocate anomaly detection, PQC, and regulatory/conformity work. | Project coordinator | Before work-package freeze | These rows have no owner or issue; approved allocation or explicit exclusion required. |
| `OQ-REV-01` Name one technical and one non-technical reviewer. | IzyLife product owner + project coordinator | Before matrix approval | Blocks issue acceptance and approval status; reviewer sign-off below. |
| `OQ-LINK-01` Add approved requirement IDs back to every implementation issue. | IzyKube product owner | Within 2 working days of matrix approval | Blocks reverse-link acceptance check; issue-body links and automated verification output. |

## Five-mapping reproduction sample

These samples demonstrate how a reviewer can reproduce the current provisional mapping without treating the working documents as commitments.

| Requirement | Source passage reproduced as a bounded paraphrase | Proposed measurement reproduction |
|---|---|---|
| `IZY-SH-POL-001` | `SRC-AI-C1` p. 3, P3 calls for policy-governed orchestration and tool trust using policy-as-code and workload identity candidates. `SRC-STARK-C1` pp. 4–6 similarly connects trust architecture with enforceable evidence. | Run the same approved fixture twice with pinned schema/OPA/bundle and compare the normalized decision and input/bundle digests (`KPI-POL-01`). |
| `IZY-SH-NOC-001` | `SRC-NOC-D1` rows 4–6, 9, 13, 15 and 17 propose NocScan for discovery, supply-chain intelligence, SBOM, and remediation. `SRC-AI-D1` row 17 proposes IzyKube orchestration/telemetry integration and identifies NocScan/CloudGSX for the scanning track. | Execute the ten named contract states and prove stale/tampered/wrong-digest states are never treated as clean (`KPI-NOC-01`). |
| `IZY-SH-EVD-001` | `SRC-STARK-C1` pp. 4–5 and 8–9 call for signed artifacts, manifests, test results, and evidence packs. `SRC-AI-C1` describes continuous assurance evidence across the lifecycle. | Independently verify every valid receipt, mutate each protected field, and prove all defined mutations fail (`KPI-EVD-01`). |
| `IZY-ST-ZT-001` | `SRC-STARK-C1` pp. 4–6 describes trust across hardware/software, supply chain, and cloud-edge operation; pp. 8–9 place integration and validation in working WPs/outcomes. | Execute an approved scenario twice from a clean cluster and compare expected decisions, traces, and verified receipts (`KPI-ST-01`). |
| `IZY-AI-CASE-001` | `SRC-AI-C1` pp. 1–3 proposes an AssuranceOps lifecycle that links agentic supply-chain events, policy, runtime monitoring, and living assurance evidence. | Validate every required evidence reference in the compliant package and prove each controlled negative variant fails for its expected reason (`KPI-AI-CASE-01`). |

## Verification procedure

Run from the repository root after each change:

```bash
# Local Markdown targets referenced by this document.
while IFS= read -r target; do
  path=${target%%#*}
  case "$path" in
    ""|http://*|https://*) continue ;;
  esac
  test -e "docs/eu/$path" || { echo "missing: $target"; exit 1; }
done < <(rg -o '\]\([^)]+\)' docs/eu/requirements-traceability.md |
  sed -E 's/^\]\((.*)\)$/\1/')

# Every linked issue must exist.
for issue in $(rg -o 'github.com/gcassata74/izykube/issues/[0-9]+' \
  docs/eu/requirements-traceability.md | sed 's@.*/@@' | sort -nu); do
  gh issue view "$issue" --repo gcassata74/izykube --json number --jq .number >/dev/null
done

# After approval, every mapped implementation issue must contain at least one approved ID.
for issue in 5 $(seq 16 33); do
  gh issue view "$issue" --repo gcassata74/izykube --json body --jq .body |
    rg -q 'IZY-(SH|ST|AI)-[A-Z]+-[0-9]{3}' || echo "missing reverse link: #$issue"
done
```

The source-passage sample must be manually reproduced against the restricted originals by an authorized reviewer. The full repository suite must also pass; documentation-only changes do not waive build verification.

## Approval state

| Gate | Reviewer / decision reference | Status | Date |
|---|---|---|---|
| Authoritative STARK source and allocation received | Not assigned | **Blocked by `OQ-ST-01`** | — |
| Authoritative AIssure source and allocation received | Not assigned | **Blocked by `OQ-AI-01` / `OQ-AI-02`** | — |
| Technical review | To be named by IzyLife/project technical board | **Pending** | — |
| Non-technical/project review | To be named by project governance | **Pending** | — |
| Product-boundary and KPI approval | Decision reference not available | **Pending** | — |

This document becomes **approved** only when both source gates are satisfied, the matrix is updated from exact authoritative locations, all mandatory/IzyKube-owned rows are identified, KPI targets are accepted, reverse links are added, and both human reviewers sign off. Until then, it is a truthful transformation baseline—not evidence that STARK or AIssure accepted the proposed work.
