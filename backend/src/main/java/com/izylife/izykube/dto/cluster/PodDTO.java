package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.Data;

@Data
public class PodDTO extends NodeDTO {

    private String assetId;

    @JsonCreator
    public PodDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("assetId") String assetId
    ) {
        super(id, name, "pod");
        this.assetId = assetId;
    }

    @Override
    public String create(KubernetesClient client) {
        // Define the container
        Container container = new ContainerBuilder()
                .withName(name)
                .withImage(assetId)  // Assuming assetId is the image name
                .addNewPort()
                .withContainerPort(80)  // Example port, adjust as necessary
                .endPort()
                .build();

        // Build the Pod object
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .addNewContainerLike(container)
                .endContainer()
                .endSpec()
                .build();

        // Serialize the Pod object to YAML
        return Serialization.asYaml(pod);
    }


}
