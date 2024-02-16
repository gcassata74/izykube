package com.izylife.izykube.services.k8s;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class K8sPodService {

    private final KubernetesClient kubernetesClient;

    @Autowired
    public K8sPodService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    public void createPod(String podName, String imageName, String namespace, Map<String, String> labels, int containerPort) throws KubernetesClientException {

        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }

        try {
            Pod pod = new PodBuilder()
                    .withNewMetadata()
                    .withName(podName)
                    .addToLabels(labels) // Add labels to the pod
                    .endMetadata()
                    .withNewSpec()
                    .addNewContainer()
                    .withName(podName)
                    .withImage(imageName)
                    .addNewPort() // Add a container port
                    .withContainerPort(containerPort)
                    .endPort()
                    .endContainer()
                    .endSpec()
                    .build();

            kubernetesClient.pods().inNamespace(namespace).create(pod);

        } catch (Exception e) {
            throw new KubernetesClientException("Failed to create pod: " + e.getMessage(), e);
        }
    }

    public List<Pod> getAllPods() {
        return kubernetesClient.pods().inAnyNamespace().list().getItems();
    }

    public Pod getPodByName(String podName, String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }
        return kubernetesClient.pods().inNamespace(namespace).withName(podName).get();
    }

    public String deletePod(String podName, String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }
        try {
            boolean deleted = kubernetesClient.pods().inNamespace(namespace).withName(podName).delete() != null;
            if (deleted) {
                return "Pod deleted: " + podName;
            } else {
                return "Pod not found: " + podName;
            }
        } catch (KubernetesClientException e) {
            return "Failed to delete pod: " + e.getMessage();
        }
    }
}