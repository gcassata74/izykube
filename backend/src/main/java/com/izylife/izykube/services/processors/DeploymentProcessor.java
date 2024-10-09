package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.VolumeDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import com.izylife.izykube.utils.ConfigMapUtils;
import com.izylife.izykube.utils.ContainerUtils;
import com.izylife.izykube.utils.VolumeUtils;
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
                .withType("RollingUpdate")
                .withNewRollingUpdate()
                .withMaxSurge(new IntOrString(1))
                .withMaxUnavailable(new IntOrString(0))
                .endRollingUpdate()
                .endStrategy()
                .endSpec()
                .build();

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
                .map(ConfigMapUtils::createEnvFromSource)
                .collect(Collectors.toList());
    }

    private List<Container> createContainers(DeploymentDTO dto) {
        List<VolumeMount> volumeMounts = dto.getSourceNodes().stream()
                .filter(node -> node instanceof VolumeDTO)
                .map(node -> (VolumeDTO) node)
                .map(VolumeUtils::createVolumeMount)
                .collect(Collectors.toList());

        return dto.getSourceNodes().stream()
                .filter(node -> node instanceof ContainerDTO)
                .map(node -> (ContainerDTO) node)
                .map(containerDTO -> {
                    Asset asset = assetRepository.findById(containerDTO.getAssetId())
                            .orElseThrow(() -> new IllegalArgumentException("Asset not found for id: " + containerDTO.getAssetId()));
                    return ContainerUtils.createContainer(containerDTO, asset, volumeMounts);
                })
                .collect(Collectors.toList());
    }

    private List<Volume> createVolumes(DeploymentDTO dto) {
        return dto.getSourceNodes().stream()
                .filter(node -> node instanceof VolumeDTO)
                .map(node -> (VolumeDTO) node)
                .map(VolumeUtils::createVolume)
                .collect(Collectors.toList());
    }
}