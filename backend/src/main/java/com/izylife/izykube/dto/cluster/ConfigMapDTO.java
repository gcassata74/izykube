package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class ConfigMapDTO extends NodeDTO {

    private List<Map<String, String>> entries;

    @JsonCreator
    public ConfigMapDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("entries") List<Map<String, String>> entries
    ) {
        super(id, name, "configmap");
        this.entries = entries;
    }

}
