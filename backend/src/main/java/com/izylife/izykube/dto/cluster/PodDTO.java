package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.Getter;
import lombok.Setter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Getter
@Setter
public class PodDTO extends NodeDTO {

    private String assetId;
    private int containerPort;

    @JsonCreator
    public PodDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("assetId") String assetId,
            @JsonProperty("containerPort") int containerPort
    ) {
        super(id, name, "pod");
        this.assetId = assetId;
        this.containerPort = containerPort;
    }

    @Override
    public String create(KubernetesClient client) {
        ConfigMap configMap = null;

        for (NodeDTO linkedNode : linkedNodes) {
            if (linkedNode instanceof ConfigMapDTO) {
                ConfigMapDTO configMapDTO = (ConfigMapDTO) linkedNode;
                String yaml = configMapDTO.create(client);
                InputStream yamlStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
                List<HasMetadata> items = client.load(yamlStream).get();
                if (!items.isEmpty() && items.get(0) instanceof ConfigMap) {
                    configMap = (ConfigMap) items.get(0);
                }
            }
        }

        // Define the container

        io.fabric8.kubernetes.api.model.Container container = new ContainerBuilder()
                .withName(name)
                .withImage(assetId)
                .addNewPort()
                .withContainerPort(containerPort)
                .endPort()
                .build();

        // Build the Pod object
        PodBuilder podBuilder = new PodBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .addNewContainerLike(container)
                .endContainer()
                .endSpec();

        if (configMap != null) {
            podBuilder = podBuilder.editSpec()
                    .addNewVolume()
                    .withName(configMap.getMetadata().getName())
                    .withNewConfigMap()
                    .withName(configMap.getMetadata().getName())
                    .endConfigMap()
                    .endVolume()
                    .endSpec();
        }

        Pod pod = podBuilder.build();
        // Serialize the Pod object to YAML
        return Serialization.asYaml(pod);
    }
}