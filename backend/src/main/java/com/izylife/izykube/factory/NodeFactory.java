package com.izylife.izykube.factory;

import com.izylife.izykube.dto.cluster.*;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class NodeFactory {

    public static NodeDTO createNodeDTO(NodeDTO node) {
        switch (node.getKind().toLowerCase()) {
            case "configmap":
                ConfigMapDTO configMap = (ConfigMapDTO) node;
                return new ConfigMapDTO(configMap.getId(), configMap.getName(), configMap.getYaml());
            case "secret":
                ConfigMapDTO secret = (ConfigMapDTO) node;
                return new SecretDTO(secret.getId(), secret.getName(), secret.getYaml());
            case "job":
                JobDTO job = (JobDTO) node;
                return new JobDTO(job.getId(), job.getName(), job.getAssetId());
            case "pod":
                PodDTO pod = (PodDTO) node;
                return convertPodToDeployment(pod);
            case "container":
                ContainerDTO container = (ContainerDTO) node;
                return new ContainerDTO(
                        container.getId(),
                        container.getName(),
                        container.getAssetId(),
                        container.getContainerPort(),
                        container.getRole()
                );
            case "deployment":
                DeploymentDTO deployment = (DeploymentDTO) node;
                return new DeploymentDTO(
                        deployment.getId(),
                        deployment.getName(),
                        deployment.getReplicas(),
                        deployment.getStrategyType(),
                        deployment.getAssetId(),
                        deployment.getContainerPort()
                );
            case "service":
                ServiceDTO service = (ServiceDTO) node;
                return new ServiceDTO(
                        service.getId(),
                        service.getName(),
                        service.getType(),
                        service.getPort(),
                        service.getNodePort(),
                        service.isExposeService(),
                        service.getFrontendUrl()
                );
            case "ingress":
                IngressDTO ingress = (IngressDTO) node;
                return new IngressDTO(
                        ingress.getId(),
                        ingress.getName(),
                        ingress.getHost(),
                        ingress.getPath(),
                        ingress.getServiceName(),
                        ingress.getServicePort(),
                        ingress.getTls(),
                        ingress.getAnnotations()
                );
            case "istio":
                VirtualServiceDTO virtualService = (VirtualServiceDTO) node;
                return new VirtualServiceDTO(
                        virtualService.getId(),
                        virtualService.getName(),
                        virtualService.getHost(),
                        virtualService.getPath(),
                        virtualService.getServiceName(),
                        virtualService.getServicePort()
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
                return new ConfigMapDTO(id, name, "");
            case "secret":
                return new SecretDTO(id, name, "");
            case "container":
                return new ContainerDTO(id, name, "", 80, null);
            case "deployment":
                return new DeploymentDTO(id, name, 1, "RollingUpdate", "", 80);
            case "service":
                return new ServiceDTO(id, name, "ClusterIP", 80);
            case "ingress":
                return new IngressDTO(id, name, "example.com", "/", "default-service", 80, null, new LinkedHashMap<>());
            case "istio":
                return new VirtualServiceDTO(id, name, "example.com", "/", "default-service", 80);
            case "volume":
                HashMap<String, Object> defaultConfig = new HashMap<>();
                defaultConfig.put("type", "emptyDir");
                defaultConfig.put("mountPath", "/mnt/data");
                defaultConfig.put("medium", "");
                defaultConfig.put("sizeLimit", "");
                return new VolumeDTO(id, name, "emptyDir", defaultConfig);
            default:
                throw new IllegalArgumentException("Unsupported node type: " + type);
        }
    }

    private static DeploymentDTO convertPodToDeployment(PodDTO pod) {
        String deploymentId = pod.getId() != null ? pod.getId() : ("deployment-" + System.nanoTime());
        String name = pod.getName() != null ? pod.getName() : deploymentId;
        return new DeploymentDTO(deploymentId, name, 1, "RollingUpdate", "", 80);
    }
}
