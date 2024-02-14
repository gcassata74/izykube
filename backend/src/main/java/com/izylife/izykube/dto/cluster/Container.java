package com.izylife.izykube.dto.cluster;

import io.fabric8.kubernetes.api.model.VolumeMount;
import lombok.Data;

import java.util.List;

@Data
public class Container extends Base {
    private String image;
    private List<ContainerPort> ports;
    private List<EnvironmentVariable> env;
    private List<VolumeMount> volumeMounts;

    @Data
    public static class ContainerPort {
        private int containerPort;
        private String protocol = "TCP"; // Default protocol, assuming TCP if not specified
    }
}