package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

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

}