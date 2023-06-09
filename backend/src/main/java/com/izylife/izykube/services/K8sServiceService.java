package com.izylife.izykube.services;

import com.izylife.izykube.constants.K8sConstants;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class K8sServiceService {

    private final KubernetesClient client;

    public K8sServiceService() {
        this.client = new DefaultKubernetesClient();
    }

    public String createService(String serviceName, String deploymentName, int port, int targetPort, String namespace) {

        if(namespace == null || namespace.isEmpty()) {
            namespace = K8sConstants.DEFAULT_NAMESPACE;
        }
        Map<String, String> selector = new HashMap<>();
        selector.put("app", deploymentName);

        io.fabric8.kubernetes.api.model.Service service = new ServiceBuilder()
                .withNewMetadata().withName(serviceName).endMetadata()
                .withNewSpec()
                .addNewPort().withPort(port).withNewTargetPort(targetPort).endPort()
                .withSelector(selector)  // This is the connection to the Deployment
                .withType("ClusterIP")
                .endSpec()
                .build();

        client.services().inNamespace(namespace).create(service);
        return "Service created successfully";
    }

    public String deleteService(String serviceName, String namespace) {
        try {
            boolean deleted = client.services().inNamespace(namespace).withName(serviceName).delete() !=null;
            if (deleted) {
                return "Service deleted: " + serviceName;
            } else {
                return "Service not found: " + serviceName;
            }
        } catch (Exception e) {
            return String.format("Service %s not deleted: %s " , serviceName, e.getMessage());
        }
    }
}
