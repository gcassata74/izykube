variable "network_name" {
  description = "Docker network shared by Terraform-managed infrastructure and Compose services."
  type        = string
  default     = "izykube"
}

variable "registry_name" {
  description = "Docker container name for the local registry."
  type        = string
  default     = "izykube-registry"
}

variable "registry_image" {
  description = "OCI image used by the local registry."
  type        = string
  default     = "registry:2"
}

variable "registry_port" {
  description = "Host port exposed by the local registry."
  type        = number
  default     = 5000
}

variable "k3s_name" {
  description = "Docker container name for the single-node k3s server."
  type        = string
  default     = "izykube-k3s"
}

variable "k3s_image" {
  description = "OCI image used by the single-node k3s server."
  type        = string
  default     = "rancher/k3s:v1.34.1-k3s1"
}

variable "kubernetes_api_port" {
  description = "Host port exposed by the k3s Kubernetes API."
  type        = number
  default     = 6443
}

variable "http_port" {
  description = "Host port exposed for HTTP ingress traffic."
  type        = number
  default     = 80
}

variable "https_port" {
  description = "Host port exposed for HTTPS ingress traffic."
  type        = number
  default     = 443
}

variable "kubeconfig_directory" {
  description = "Absolute path, or path relative to this Terraform root, where k3s writes config."
  type        = string
  default     = ""
}
