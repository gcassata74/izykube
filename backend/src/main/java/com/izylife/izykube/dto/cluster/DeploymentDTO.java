package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DeploymentDTO extends NodeDTO {

    String assetId;
    int replicas;

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
}
