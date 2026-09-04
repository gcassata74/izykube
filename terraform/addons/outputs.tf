output "kubeconfig_path" {
  description = "Host kubeconfig used by the addon providers."
  value       = local.kubeconfig_path
}

output "managed_components" {
  description = "Terraform-managed cluster addon groups."
  value = [
    "OLM/operator lifecycle",
    "cert-manager and internal CA",
    "Istio control plane and ingress gateway",
    "Prometheus and Grafana",
  ]
}

output "gateway" {
  description = "Shared Istio ingress Gateway."
  value       = "${var.gateway_namespace}/${var.gateway_name}"
}
