# CRD Editor (Model-based)

## Overview

CRDs are authored via a structured form + schema builder (no YAML editor). The application persists an internal model to MongoDB and generates Kubernetes `CustomResourceDefinition` YAML internally.

## Data model (MongoDB)

Stored in the `crds` collection as `CrdDefinition`:

- `group` (string)
- `singularName` (string)
- `scope` (string: `Namespaced|Cluster`)
- `version` (string, default `v1`)
- `schemaFields` (array of `{ fieldName, fieldType }`)

See:
- `backend/src/main/java/com/izylife/izykube/model/CrdDefinition.java`
- `backend/src/main/java/com/izylife/izykube/model/CrdSchemaField.java`
- `backend/src/main/java/com/izylife/izykube/enums/CrdFieldType.java`

## Derivation rules

- `plural = singularName + "s"`
- `kind = PascalCase(singularName)` (splits on non-word/underscore)
- `metadata.name = plural + "." + group`

Backend: `backend/src/main/java/com/izylife/izykube/services/CrdDerivationService.java`  
Frontend (preview-only): `frontend/src/app/crds/crd-generator.ts`

## YAML generation

YAML is generated from the model by `CrdYamlGenerator`:

- `apiVersion: apiextensions.k8s.io/v1`
- `kind: CustomResourceDefinition`
- `metadata.name = <plural>.<group>`
- `spec.group`, `spec.scope`, `spec.names`, `spec.versions[0]`
- `openAPIV3Schema.properties.spec.properties` is built from `schemaFields`

See: `backend/src/main/java/com/izylife/izykube/services/CrdYamlGenerator.java`

Optional preview endpoint:
- `GET /api/crds/{id}/yaml` (text/plain)

## Frontend editor UI

Routes:
- `/crds/new`
- `/crds/:id/edit`

Sections:
- Base fields: group, singular name, scope, version + computed preview (plural/kind/metadata.name)
- Schema builder: repeatable blocks (fieldName + fieldType) using the same FormArray pattern as the ConfigMap/ConfigBundle editor
- Optional YAML preview: read-only `<textarea>` (no Ace/Monaco)

See:
- `frontend/src/app/crds/crd-editor/crd-editor.component.ts`
- `frontend/src/app/crds/crd-editor/crd-editor.component.html`

