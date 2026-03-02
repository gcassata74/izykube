package com.izylife.izykube.dto.kube;

public record WorkloadHealthDTO(
        String kind,
        String name,
        String namespace,
        boolean unhealthy,
        String reason
) {
}
