package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.VolumeDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Processor(DeploymentDTO.class)
@Service
@AllArgsConstructor
public class DeploymentProcessor implements TemplateProcessor<DeploymentDTO> {

    private final AssetRepository assetRepository;

    @Override
    public String createTemplate(DeploymentDTO dto) {
        List<Container> containers = createContainers(dto);
        List<EnvFromSource> envFromSources = createEnvFromSources(dto);
        List<Volume> volumes = createVolumes(dto);


        if (containers.isEmpty()) {
            throw new IllegalArgumentException("Deployment must have at least one linked Container");
        }

        Map<String, String> labels = new HashMap<>();
        labels.put("app", dto.getName());

        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withReplicas(dto.getReplicas())
                .withNewSelector()
                .withMatchLabels(labels)
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withContainers(containers)
                .withRestartPolicy("Always")
                .endSpec()
                .endTemplate()
                .withNewStrategy()
                .withType(dto.getStrategyType())
                .endStrategy()
                .endSpec()
                .build();

        //use the same env for all the containers (maybe we need to change it)
        //TODO connect the volumes and configmaps to the containers and allow multiple connections
        deployment.getSpec().getTemplate().getSpec().getContainers()
                .forEach(container -> container.setEnvFrom(envFromSources));

        if (!volumes.isEmpty()) {
            deployment.getSpec().getTemplate().getSpec().setVolumes(volumes);
        }

        return Serialization.asYaml(deployment);
    }


    private List<EnvFromSource> createEnvFromSources(DeploymentDTO dto) {
        return dto.getSourceNodes().stream()
                .filter(node -> node instanceof ConfigMapDTO)
                .map(node -> (ConfigMapDTO) node)
                .map(configMap -> new EnvFromSourceBuilder()
                        .withNewConfigMapRef()
                        .withName(configMap.getName())
                        .endConfigMapRef()
                        .build())
                .collect(Collectors.toList());
    }

    private List<Container> createContainers(DeploymentDTO dto) {
        return dto.getSourceNodes().stream()
                .filter(node -> node instanceof ContainerDTO)
                .map(node -> (ContainerDTO) node)
                .map(containerDTO -> createContainer(containerDTO, dto))
                .collect(Collectors.toList());
    }

    private Container createContainer(ContainerDTO containerDTO, DeploymentDTO deploymentDTO) {
        Asset asset = assetRepository.findById(containerDTO.getAssetId())
                .orElseThrow(() -> new IllegalArgumentException("Asset not found for id: " + containerDTO.getAssetId()));

        List<VolumeMount> volumeMounts = deploymentDTO.getSourceNodes().stream()
                .filter(node -> node instanceof VolumeDTO)
                .map(node -> (VolumeDTO) node)
                .map(this::createVolumeMount)
                .collect(Collectors.toList());

        Container container = new ContainerBuilder()
                .withName(containerDTO.getName())
                .withImage(asset.getImage())
                .addNewPort()
                .withContainerPort(containerDTO.getContainerPort())
                .endPort()
                .build();

        if (!volumeMounts.isEmpty()) {
            container.setVolumeMounts(volumeMounts);
        }

        return container;
    }

    private List<Volume> createVolumes(DeploymentDTO dto) {
        return dto.getSourceNodes().stream()
                .filter(node -> node instanceof VolumeDTO)
                .map(node -> (VolumeDTO) node)
                .map(this::createVolume)
                .collect(Collectors.toList());
    }

    private Volume createVolume(VolumeDTO volumeDTO) {
        VolumeBuilder volumeBuilder = new VolumeBuilder()
                .withName(volumeDTO.getName());

        switch (volumeDTO.getType()) {
            case "emptyDir":
                volumeBuilder.withNewEmptyDir()
                        .withMedium((String) volumeDTO.getConfig().get("medium"))
                        .withSizeLimit(new Quantity((String) volumeDTO.getConfig().get("sizeLimit")))
                        .endEmptyDir();
                break;
            case "hostPath":
                volumeBuilder.withNewHostPath()
                        .withPath((String) volumeDTO.getConfig().get("path"))
                        .withType((String) volumeDTO.getConfig().get("hostPathType"))
                        .endHostPath();
                break;
            case "configMap":
                volumeBuilder.withNewConfigMap()
                        .withName((String) volumeDTO.getConfig().get("name"))
                        .endConfigMap();
                break;
            case "secret":
                volumeBuilder.withNewSecret()
                        .withSecretName((String) volumeDTO.getConfig().get("secretName"))
                        .endSecret();
                break;
            case "persistentVolumeClaim":
                volumeBuilder.withNewPersistentVolumeClaim()
                        .withClaimName((String) volumeDTO.getConfig().get("claimName"))
                        .withReadOnly((Boolean) volumeDTO.getConfig().get("readOnly"))
                        .endPersistentVolumeClaim();
                break;
            default:
                throw new IllegalArgumentException("Unsupported volume type: " + volumeDTO.getType());
        }

        return volumeBuilder.build();
    }

    private VolumeMount createVolumeMount(VolumeDTO volumeDTO) {
        return new VolumeMountBuilder()
                .withName(volumeDTO.getName())
                .withMountPath((String) volumeDTO.getConfig().get("mountPath"))
                .withReadOnly(volumeDTO.getType().equals("persistentVolumeClaim")
                        && volumeDTO.getConfig().containsKey("readOnly")
                        ? (Boolean) volumeDTO.getConfig().get("readOnly")
                        : null)
                .build();
    }

}