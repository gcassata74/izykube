package com.izylife.izykube.services;


import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
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


    public String createPod(String podName, String imageName, String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }

        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName(podName)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(podName)
                .withImage(imageName)
                .endContainer()
                .endSpec()
                .build();

        pod = kubernetesClient.pods().inNamespace(namespace).create(pod);

        return "Created pod " + pod.getMetadata().getName() + " in namespace " + namespace;
    }


    public String createDeployment(String deploymentName, String imageName, String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }

        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(deploymentName)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                .addToMatchLabels("app", deploymentName)
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("app", deploymentName)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(deploymentName)
                .withImage(imageName)
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



}
