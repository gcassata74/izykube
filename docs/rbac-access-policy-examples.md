# RBAC: Access Policy examples

## Workload target (Deployment)

Input: AccessPolicy `my-app-reader` linked to Deployment `my-app` in namespace `demo`.

Expected generated resources:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: my-app-sa
  namespace: demo
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: my-app-reader
  namespace: demo
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get","list","watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: my-app-reader-my-app-rb
  namespace: demo
subjects:
- kind: ServiceAccount
  name: my-app-sa
  namespace: demo
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: my-app-reader
```

Workload patch:

```yaml
spec:
  template:
    spec:
      serviceAccountName: my-app-sa
```

Note: only workload targets are supported by the UI; the processor generates `RoleBinding` subjects of type `ServiceAccount` and patches the workload to use it.
