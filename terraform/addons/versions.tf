terraform {
  required_version = ">= 1.6.0"

  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.17"
    }
    http = {
      source  = "hashicorp/http"
      version = "~> 3.5"
    }
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "~> 1.19"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.37"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.1"
    }
  }
}

provider "kubernetes" {
  config_path    = local.kubeconfig_path
  config_context = var.kube_context == "" ? null : var.kube_context
}

provider "helm" {
  kubernetes {
    config_path    = local.kubeconfig_path
    config_context = var.kube_context == "" ? null : var.kube_context
  }
}

provider "kubectl" {
  load_config_file = true
  config_path      = local.kubeconfig_path
  config_context   = var.kube_context == "" ? null : var.kube_context
}
