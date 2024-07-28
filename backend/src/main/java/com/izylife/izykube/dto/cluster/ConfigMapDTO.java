package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ConfigMapDTO extends NodeDTO {

    List<ConfigMapEntryDTO> entries;

    @JsonCreator
    public ConfigMapDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("entries") List<ConfigMapEntryDTO> entries
    ) {
        super(id, name, "configmap");
        this.entries = entries;
    }
}
