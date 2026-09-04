variable "kubeconfig_path" {
  description = "Absolute path, or path relative to this Terraform root, to the host-side k3s kubeconfig."
  type        = string
  default     = ""
}

variable "kube_context" {
  description = "Optional kubeconfig context. Leave empty to use the current context."
  type        = string
  default     = ""
}

variable "olm_version" {
  description = "OLM release tag whose CRDs and deployment manifests are applied."
  type        = string
  default     = "v0.30.0"
}

variable "cert_manager_version" {
  description = "cert-manager Helm chart version."
  type        = string
  default     = "v1.17.2"
}

variable "istio_version" {
  description = "Istio Helm chart version used for the base, control plane, and ingress gateway."
  type        = string
  default     = "1.18.2"
}

variable "prometheus_stack_version" {
  description = "kube-prometheus-stack Helm chart version."
  type        = string
  default     = "70.4.2"
}

variable "grafana_version" {
  description = "Grafana Helm chart version."
  type        = string
  default     = "8.10.1"
}

variable "monitoring_namespace" {
  description = "Namespace for Prometheus and Grafana."
  type        = string
  default     = "istio-system-db"
}

variable "gateway_name" {
  description = "Shared Istio ingress Gateway name."
  type        = string
  default     = "izykube-gateway"
}

variable "gateway_namespace" {
  description = "Namespace for the shared Istio ingress Gateway."
  type        = string
  default     = "istio-system"
}

variable "ca_common_name" {
  description = "Common name for the Terraform-managed internal CA."
  type        = string
  default     = "izykube-internal-ca"
}
