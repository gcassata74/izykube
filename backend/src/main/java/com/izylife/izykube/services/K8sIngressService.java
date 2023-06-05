package com.izylife.izykube.services;

import com.izylife.izykube.constants.K8sConstants;
import com.izylife.izykube.model.ServiceInfo;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class K8sIngressService {

    private final KubernetesClient kubernetesClient;

    public K8sIngressService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    public void createIngress(List<ServiceInfo> services, String namespace) {
        if(namespace == null || namespace.isEmpty()) {
            namespace = K8sConstants.DEFAULT_NAMESPACE;
        }
        IngressBuilder ingressBuilder = new IngressBuilder().withNewMetadata().withName("example-ingress").endMetadata();

        services.forEach(service ->
                ingressBuilder.editOrNewSpec().addNewRule()
                        .withHost(service.getHost())
                        .withNewHttp()
                        .addNewPath()
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
                        .endPath()
                        .endHttp()
                        .endRule());

        Ingress ingress = ingressBuilder.build();
        kubernetesClient.network().v1().ingresses().inNamespace(namespace).create(ingress);
    }

}
