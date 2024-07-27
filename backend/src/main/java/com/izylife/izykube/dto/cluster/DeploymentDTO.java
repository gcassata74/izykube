package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeploymentDTO extends NodeDTO {

    private String assetId;
    private int replicas;
    private int containerPort;
    private Map<String, String> resources;

    @JsonCreator
    public DeploymentDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("assetId") String assetId,
            @JsonProperty("replicas") int replicas,
            @JsonProperty("containerPort") int containerPort,
            @JsonProperty("resources") Map<String, String> resources
    ) {
        super(id, name, "deployment");
        this.assetId = assetId;
        this.replicas = replicas;
        this.containerPort = containerPort;
        this.resources = resources;
    }
}