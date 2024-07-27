package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IngressDTO extends NodeDTO {
    private String host;
    private String path;

    @JsonCreator
    public IngressDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("host") String host,
            @JsonProperty("path") String path
    ) {
        super(id, name, "ingress");
        this.host = host;
        this.path = path;
    }
}