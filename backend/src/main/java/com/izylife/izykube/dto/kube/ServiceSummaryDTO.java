package com.izylife.izykube.dto.kube;

public record ServiceSummaryDTO(
        String name,
        String namespace,
        String type,
        String clusterIp,
        String externalIp,
        String ports,
        String age
) {
}
