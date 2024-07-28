package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.PodDTO;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

@Processor(PodDTO.class)
@Service
public class PodProcessor implements TemplateProcessor<PodDTO> {

    private ConfigMapProcessor configMapProcessor;

    public String createTemplate(PodDTO dto) {
        StringBuilder fullYaml = new StringBuilder();
        for (NodeDTO linkedNode : dto.getLinkedNodes()) {
            if (linkedNode instanceof ConfigMapDTO) {
                ConfigMapDTO configMapDTO = (ConfigMapDTO) linkedNode;
                String configMapYaml = configMapProcessor.createTemplate(configMapDTO);
                fullYaml.append(configMapYaml);
            }
        }
        // Define the container
        io.fabric8.kubernetes.api.model.Container container = new ContainerBuilder()
                .withName(dto.getName())
                .withImage(dto.getAssetId())
                .addNewPort()
                .withContainerPort(dto.getContainerPort())
                .endPort()
                .build();

        // Build the Pod object
        PodBuilder podBuilder = new PodBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .addNewContainerLike(container)
                .endContainer()
                .endSpec();

        Pod pod = podBuilder.build();
        // Serialize the Pod object to YAML
        return Serialization.asYaml(pod);
    }
}
