package com.izylife.izykube.services;


import com.izylife.izykube.dto.DeploymentRequest;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class K8sDeploymentService {

    private final KubernetesClient kubernetesClient;

    public K8sDeploymentService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    public List<Deployment> getDeployments(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }
        DeploymentList deploymentList = this.kubernetesClient.apps().deployments().inNamespace(namespace).list();
        return deploymentList.getItems();
    }


    public String createDeployment(DeploymentRequest request) {
        String namespace = request.getNamespace();
        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }
        int replicas = request.getReplicas() != null ? request.getReplicas() : 1;
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(request.getDeploymentName())
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withReplicas(replicas)
                .withNewSelector()
                .addToMatchLabels("app", request.getDeploymentName())
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("app", request.getDeploymentName())
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(request.getDeploymentName())
                .withImage(request.getImageName())
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        deployment = kubernetesClient.apps().deployments().inNamespace(namespace).create(deployment);

        return "Created deployment " + deployment.getMetadata().getName() + " in namespace " + namespace;
    }


    public boolean deleteDeployment(String deploymentName, String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }
        return kubernetesClient.apps().deployments().inNamespace(namespace).withName(deploymentName).delete() != null;
    }

    public String updateReplicas(String deploymentName, int replicas, String namespace) {

        Deployment deployment;
        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }

        try {
            deployment = kubernetesClient.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
            if (deployment == null) {
                return "Deployment not found";
            }

            deployment.getSpec().setReplicas(replicas);
            kubernetesClient.apps().deployments().inNamespace(namespace).withName(deploymentName).replace(deployment);
        } catch (Exception e) {
            return String.format("Deployment %s not updated: %s ", deploymentName, e.getMessage());
        }
        return String.format("Updated replicas of deployment %s, to %s  in namespace  %s" + deployment.getMetadata().getName(), replicas, namespace);
    }

}
