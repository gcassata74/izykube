package com.izylife.izykube.factory;

import io.fabric8.istio.client.IstioClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

@Component
public class ClientFactory {
    private final KubernetesClient kubernetesClient;
    private final IstioClient istioClient;

    public ClientFactory(KubernetesClient kubernetesClient, IstioClient istioClient) {
        this.kubernetesClient = kubernetesClient;
        this.istioClient = istioClient;
    }

    public Object getClient(String apiVersion) {
        if (apiVersion.startsWith("networking.istio.io")) {
            return istioClient;
        } else {
            return kubernetesClient;
        }
    }
}
