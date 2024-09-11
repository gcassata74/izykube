package com.izylife.izykube.factory;

import com.izylife.izykube.dto.cluster.*;

import java.util.HashMap;
import java.util.ArrayList;

public class NodeFactory {

    public static NodeDTO createNodeDTO(NodeDTO node) {
        switch (node.getKind().toLowerCase()) {
            case "configmap":
                ConfigMapDTO configMap = (ConfigMapDTO) node;
                return new ConfigMapDTO(configMap.getId(), configMap.getName(), configMap.getEntries());
            case "pod":
                PodDTO pod = (PodDTO) node;
                return new PodDTO(
                        pod.getId(),
                        pod.getName(),
                        pod.getRestartPolicy(),
                        pod.getServiceAccountName(),
                        pod.getNodeSelector(),
                        pod.getHostNetwork(),
                        pod.getDnsPolicy(),
                        pod.getSchedulerName(),
                        pod.getPriority(),
                        pod.getPreemptionPolicy()
                );
            case "container":
                ContainerDTO container = (ContainerDTO) node;
                return new ContainerDTO(
                        container.getId(),
                        container.getName(),
                        container.getAssetId(),
                        container.getContainerPort()
                );
            case "deployment":
                DeploymentDTO deployment = (DeploymentDTO) node;
                return new DeploymentDTO(
                        deployment.getId(),
                        deployment.getName(),
                        deployment.getReplicas(),
                        deployment.getStrategyType()
                );
            case "service":
                ServiceDTO service = (ServiceDTO) node;
                return new ServiceDTO(
                        service.getId(),
                        service.getName(),
                        service.getType(),
                        service.getPort(),
                        service.getNodePort()
                );
            case "ingress":
                IngressDTO ingress = (IngressDTO) node;
                return new IngressDTO(
                        ingress.getId(),
                        ingress.getName(),
                        ingress.getHost(),
                        ingress.getPath(),
                        ingress.getServiceName(),
                        ingress.getServicePort()
                );
            case "volume":
                VolumeDTO volume = (VolumeDTO) node;
                return new VolumeDTO(
                        volume.getId(),
                        volume.getName(),
                        volume.getType(),
                        volume.getConfig()
                );
            default:
                throw new IllegalArgumentException("Unsupported node type: " + node.getKind());
        }
    }

    // Helper method to create a new node with default values
    public static NodeDTO createNewNode(String type, String id, String name) {
        switch (type.toLowerCase()) {
            case "configmap":
                return new ConfigMapDTO(id, name, new ArrayList<>());
            case "pod":
                return new PodDTO(id, name, "Always");
            case "container":
                return new ContainerDTO(id, name, "", 80);
            case "deployment":
                return new DeploymentDTO(id, name, 1, "RollingUpdate");
            case "service":
                return new ServiceDTO(id, name, "ClusterIP", 80);
            case "ingress":
                return new IngressDTO(id, name, "example.com", "/", "default-service", 80);
            case "volume":
                return new VolumeDTO(id, name, "emptyDir", new HashMap<>());
            default:
                throw new IllegalArgumentException("Unsupported node type: " + type);
        }
    }
}