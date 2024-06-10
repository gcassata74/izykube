package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Data;

@Data
public class DeploymentDTO extends NodeDTO {

    String assetId;
    int replicas;
    @JsonCreator
    public DeploymentDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("assetId") String assetId,
            @JsonProperty("replicas") int replicas
    ) {
        super(id, name, "deployment");
        this.assetId = assetId;
        this.replicas = replicas;
    }

    @Override
    public String create(KubernetesClient client) {
        return "";
    }
}
