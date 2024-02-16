package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Container extends Base {
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


//    private List<ContainerPort> ports;
//    private List<EnvironmentVariable> env;
//    private List<VolumeMount> volumeMounts;

//    @Data
//    public static class ContainerPort {
//        private int containerPort;
//        private String protocol = "TCP"; // Default protocol, assuming TCP if not specified
//    }
}