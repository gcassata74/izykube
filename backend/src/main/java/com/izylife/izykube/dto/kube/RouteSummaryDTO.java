package com.izylife.izykube.dto.kube;

public record RouteSummaryDTO(
        String name,
        String namespace,
        String hosts,
        String serviceTargets,
        String gatewayName,
        String path,
        String tls,
        String age
) {
}
