package com.izylife.izykube.services;

import com.izylife.izykube.dto.IngressRequest;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;

@Service
public class K8sIngressService {

    private final KubernetesClient kubernetesClient;

    public K8sIngressService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }


    public String createIngress(IngressRequest ingressRequest, String namespace) {
        return "to be implemented";
    }
}
