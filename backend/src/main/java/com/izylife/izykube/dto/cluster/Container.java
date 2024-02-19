package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Container extends Node {
    private String assetId;

    @JsonCreator
    public Container(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("assetId") String assetId
    ) {
        super(id, name, "Container");
        this.assetId = assetId;
    }

}