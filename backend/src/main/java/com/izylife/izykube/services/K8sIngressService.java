package com.izylife.izykube.services;

import com.izylife.izykube.constants.K8sConstants;
import com.izylife.izykube.model.IngressRequest;
import com.izylife.izykube.model.ServiceInfo;
import io.fabric8.kubernetes.api.model.networking.v1.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class K8sIngressService {

    private final KubernetesClient kubernetesClient;

    public K8sIngressService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }


    public String createIngress(IngressRequest ingressRequest, String namespace) {
        if(namespace == null || namespace.isEmpty()) {
            namespace = K8sConstants.DEFAULT_NAMESPACE;
        }
        try {
            List<IngressRule> ingressRules = new ArrayList<>();
            for (ServiceInfo service : ingressRequest.getServices()) {
                HTTPIngressPath httpIngressPath = new HTTPIngressPathBuilder()
                        .withPathType("Prefix")
                        .withPath(service.getPath())
                        .withNewBackend()
                        .withNewService()
                        .withName(service.getName())
                        .withNewPort()
                        .withNumber(service.getPort())
                        .endPort()
                        .endService()
                        .endBackend()
                        .build();

                HTTPIngressRuleValue httpIngressRuleValue = new HTTPIngressRuleValueBuilder()
                        .withPaths(httpIngressPath)
                        .build();

                IngressRule ingressRule = new IngressRuleBuilder()
                        .withHost(service.getHost())
                        .withHttp(httpIngressRuleValue)
                        .build();

                ingressRules.add(ingressRule);
            }

            Ingress ingress = new IngressBuilder()
                    .withNewMetadata()
                    .withName(ingressRequest.getName())
                    .endMetadata()
                    .withNewSpec()
                    .withRules(ingressRules)
                    .endSpec()
                    .build();

            kubernetesClient.network().v1().ingresses().inNamespace(namespace).create(ingress);
            return String.format("Ingress %s created successfully", ingressRequest.getName());
        } catch (Exception e) {
            return String.format("Ingress not created: %s ", e.getMessage());
        }
    }
}
