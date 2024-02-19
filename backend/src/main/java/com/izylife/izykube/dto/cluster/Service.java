package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Service extends Node {

    @JsonCreator
    public Service(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name
    ) {
        super(id, name, "service");
    }
}
