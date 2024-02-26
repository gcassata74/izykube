package com.izylife.izykube.services.k8s;


import com.izylife.izykube.dto.cluster.ConfigMap;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.services.AssetService;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class K8sDeploymentService {

    @Autowired
    private KubernetesClient kubernetesClient;

    @Autowired
    private AssetService assetService;

    private List<ConfigMap> configMaps;


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


    public Deployment createDeployment(com.izylife.izykube.dto.cluster.Deployment deployment) {
        Asset asset = assetService.getAsset(deployment.getAssetId());
        return createDeployment("default", deployment.getName(), asset.getImage(), deployment.getReplicas());
    }

    public Deployment createDeployment(String namespace, String deploymentName, String imageName, int replicas) {

        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(deploymentName)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withReplicas(replicas)
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
        return kubernetesClient.apps().deployments().inNamespace(namespace).create(deployment);
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

    public void attachConfigMapToDeployment(ConfigMap sourceNode, com.izylife.izykube.dto.cluster.Deployment deployment) {
    }

    public void addConfigMap(ConfigMap sourceNode) {
        // check if the configMap already exists in the list
        // are we sure we can compare the objects like this?
        if (configMaps.contains(sourceNode)) {
            return;
        }
        configMaps.add(sourceNode);
    }
}
