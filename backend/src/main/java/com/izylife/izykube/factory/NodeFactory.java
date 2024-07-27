package com.izylife.izykube.factory;

import com.izylife.izykube.dto.cluster.*;

public class NodeFactory {

    public static NodeDTO createNodeDTO(NodeDTO node) {
        switch (node.getKind().toLowerCase()) {
            case "configmap":
                ConfigMapDTO configMap = (ConfigMapDTO) node;
                return new ConfigMapDTO(configMap.getId(), configMap.getName(), configMap.getEntries());
            case "pod":
                PodDTO pod = (PodDTO) node;
                return new PodDTO(pod.getId(), pod.getName(), pod.getAssetId(), pod.getContainerPort());
            case "deployment":
                DeploymentDTO deployment = (DeploymentDTO) node;
                return new DeploymentDTO(
                        deployment.getId(),
                        deployment.getName(),
                        deployment.getAssetId(),
                        deployment.getReplicas(),
                        deployment.getContainerPort(),
                        deployment.getResources()
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
                        ingress.getPath()
                );
            default:
                throw new IllegalArgumentException("Unsupported node type: " + node.getKind());
        }
    }
}