package com.izylife.izykube.factory;

import com.izylife.izykube.dto.cluster.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public class NodeFactory {

    public static NodeDTO createNodeDTO(NodeDTO node) {
        NodeDTO sanitized;
        switch (node.getKind().toLowerCase()) {
            case "configmap":
                ConfigMapDTO configMap = (ConfigMapDTO) node;
                ConfigMapDTO sanitizedConfig = new ConfigMapDTO(configMap.getId(), configMap.getName(), configMap.getYaml());
                sanitizedConfig.setEntries(cloneEntries(configMap.getEntries()));
                sanitizedConfig.setShowSecretsAsPlain(configMap.getShowSecretsAsPlain());
                sanitized = sanitizedConfig;
                break;
            case "secret":
                ConfigMapDTO secret = (ConfigMapDTO) node;
                SecretDTO sanitizedSecret = new SecretDTO(secret.getId(), secret.getName(), secret.getYaml());
                sanitizedSecret.setEntries(cloneEntries(secret.getEntries()));
                sanitizedSecret.setShowSecretsAsPlain(secret.getShowSecretsAsPlain());
                sanitized = sanitizedSecret;
                break;
            case "job":
                JobDTO job = (JobDTO) node;
                sanitized = new JobDTO(job.getId(), job.getName(), job.getAssetId());
                break;
            case "pod":
                PodDTO pod = (PodDTO) node;
                sanitized = convertPodToDeployment(pod);
                break;
            case "container":
                ContainerDTO container = (ContainerDTO) node;
                sanitized = new ContainerDTO(
                        container.getId(),
                        container.getName(),
                        container.getAssetId(),
                        container.getContainerPort(),
                        container.getRole()
                );
                break;
            case "deployment":
                DeploymentDTO deployment = (DeploymentDTO) node;
                sanitized = new DeploymentDTO(
                        deployment.getId(),
                        deployment.getName(),
                        deployment.getReplicas(),
                        deployment.getStrategyType(),
                        deployment.getAssetId(),
                        deployment.getContainerPort(),
                        deployment.getWorkloadType()
                );
                break;
            case "service":
                ServiceDTO service = (ServiceDTO) node;
                sanitized = new ServiceDTO(
                        service.getId(),
                        service.getName(),
                        service.getType(),
                        service.getPort(),
                        service.getNodePort(),
                        service.isExposeService(),
                        service.getFrontendUrl()
                );
                break;
            case "ingress":
                IngressDTO ingress = (IngressDTO) node;
                sanitized = new IngressDTO(
                        ingress.getId(),
                        ingress.getName(),
                        ingress.getHost(),
                        ingress.getPath(),
                        ingress.getServiceName(),
                        ingress.getServicePort(),
                        ingress.getTls(),
                        ingress.getAnnotations()
                );
                break;
            case "istio":
                VirtualServiceDTO virtualService = (VirtualServiceDTO) node;
                sanitized = new VirtualServiceDTO(
                        virtualService.getId(),
                        virtualService.getName(),
                        virtualService.getHost(),
                        virtualService.getPath(),
                        virtualService.getServiceName(),
                        virtualService.getServicePort()
                );
                break;

            case "volume":
                VolumeDTO volume = (VolumeDTO) node;
                sanitized = new VolumeDTO(
                        volume.getId(),
                        volume.getName(),
                        volume.getType(),
                        volume.getConfig()
                );
                break;
            default:
                throw new IllegalArgumentException("Unsupported node type: " + node.getKind());
        }
        sanitized.setAffected(node.isAffected());
        return sanitized;
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
                return new DeploymentDTO(id, name, 1, "RollingUpdate", "", 80, DeploymentWorkloadType.DEPLOYMENT);
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
        return new DeploymentDTO(deploymentId, name, 1, "RollingUpdate", "", 80, DeploymentWorkloadType.DEPLOYMENT);
    }

    private static List<ConfigEntryDTO> cloneEntries(List<ConfigEntryDTO> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return entries.stream().map(entry -> {
            ConfigEntryDTO clone = new ConfigEntryDTO();
            clone.setKey(entry.getKey());
            clone.setValue(entry.getValue());
            clone.setSensitivity(entry.getSensitivity());
            return clone;
        }).collect(Collectors.toList());
    }
}
