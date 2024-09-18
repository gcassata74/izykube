package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
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
                .withRestartPolicy("Always") // Default for Deployments
                // Other pod-level specifications can be added here if needed
                .endSpec()
                .endTemplate()
                .withNewStrategy()
                .withType(dto.getStrategyType())
                .endStrategy()
                .endSpec()
                .build();

        return Serialization.asYaml(deployment);
    }

    private List<Container> createContainers(DeploymentDTO dto) {
        return dto.getSourceNodes().stream()
                .filter(node -> node instanceof ContainerDTO)
                .map(node -> (ContainerDTO) node)
                .map(this::createContainer)
                .collect(Collectors.toList());
    }

    private Container createContainer(ContainerDTO containerDTO) {
        Asset asset = assetRepository.findById(containerDTO.getAssetId())
                .orElseThrow(() -> new IllegalArgumentException("Asset not found for id: " + containerDTO.getAssetId()));

        return new ContainerBuilder()
                .withName(containerDTO.getName())
                .withImage(asset.getImage())
                .addNewPort()
                .withContainerPort(containerDTO.getContainerPort())
                .endPort()
                .build();
    }
}