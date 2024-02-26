package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Deployment extends Node {

    String assetId;
    int replicas;

    public Deployment(
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
