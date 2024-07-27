package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceDTO extends NodeDTO {

    private String type;
    private int port;
    private Integer nodePort;

    @JsonCreator
    public ServiceDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("port") int port,
            @JsonProperty("nodePort") Integer nodePort
    ) {
        super(id, name, "service");
        this.type = type;
        this.port = port;
        this.nodePort = nodePort;
    }
}