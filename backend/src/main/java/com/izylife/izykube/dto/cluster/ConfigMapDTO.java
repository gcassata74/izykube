package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfigMapDTO extends NodeDTO {

    String yaml;

    @JsonCreator
    public ConfigMapDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("yaml") String yaml
    ) {
        super(id, name, "configmap");
        this.yaml = yaml;
    }
}
