package com.izylife.izykube.services;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PodService {

    private final KubernetesClient kubernetesClient;

    @Autowired
    public PodService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    public String createPod(String podName, String imageName, String namespace) {

        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }

        try {
            Pod pod = new PodBuilder()
                    .withNewMetadata()
                    .withName(podName)
                    .endMetadata()
                    .withNewSpec()
                    .addNewContainer()
                    .withName(podName)
                    .withImage(imageName)
                    .endContainer()
                    .endSpec()
                    .build();

            pod = kubernetesClient.pods().inNamespace(namespace).create(pod);

            return "Pod created: " + podName;
        } catch (KubernetesClientException e) {
            return "Failed to create pod: " + e.getMessage();
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