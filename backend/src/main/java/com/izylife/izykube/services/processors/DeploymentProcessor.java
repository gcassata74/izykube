package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Processor(DeploymentDTO.class)
@AllArgsConstructor
@org.springframework.stereotype.Service
public class DeploymentProcessor implements TemplateProcessor<DeploymentDTO> {

    private final ConfigMapProcessor configMapProcessor;
    private final AssetRepository assetRepository;

    @Override
    public String createTemplate(DeploymentDTO dto) {
        StringBuilder fullYaml = new StringBuilder();
        Map<String, String> labels = Map.of("app", dto.getName());
        List<EnvFromSource> envFromSources = new ArrayList<>();

        Asset asset = assetRepository.findById(dto.getAssetId()).orElseThrow();

        for (NodeDTO linkedNode : dto.getLinkedNodes()) {
            if (linkedNode instanceof ConfigMapDTO) {
                ConfigMapDTO configMapDTO = (ConfigMapDTO) linkedNode;
                String configMapYaml = configMapProcessor.createTemplate(configMapDTO);
                fullYaml.append(configMapYaml);

                // Add ConfigMap as an environment source
                envFromSources.add(new EnvFromSourceBuilder()
                        .withNewConfigMapRef()
                        .withName(configMapDTO.getName())
                        .endConfigMapRef()
                        .build());
            } else if (linkedNode instanceof ServiceDTO) {
                ServiceDTO serviceDTO = (ServiceDTO) linkedNode;
                Service service = new ServiceBuilder()
                        .withNewMetadata()
                        .withName(serviceDTO.getName())
                        .withNamespace("default")
                        .endMetadata()
                        .withNewSpec()
                        .withType(serviceDTO.getType())
                        .addNewPort()
                        .withPort(serviceDTO.getPort())
                        .withNodePort(serviceDTO.getNodePort())
                        .endPort()
                        .withSelector(labels)
                        .endSpec()
                        .build();
                fullYaml.append(Serialization.asYaml(service));
            }
        }

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
                .addNewContainer()
                .withName(dto.getName())
                .withImage(asset.getImage())
                .addNewPort()
                .withContainerPort(dto.getContainerPort())
                .endPort()
                .withResources(createResourceRequirements(dto))
                .withEnvFrom(envFromSources)  // Add environment variables from ConfigMaps
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        fullYaml.append(Serialization.asYaml(deployment));
        return fullYaml.toString();
    }

    private ResourceRequirements createResourceRequirements(DeploymentDTO dto) {
        return new ResourceRequirementsBuilder()
                .addToRequests("cpu", new Quantity(dto.getResources().get("cpu")))
                .addToRequests("memory", new Quantity(dto.getResources().get("memory")))
                .build();
    }

}