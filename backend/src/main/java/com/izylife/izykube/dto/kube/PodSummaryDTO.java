package com.izylife.izykube.dto.kube;

public record PodSummaryDTO(
        String name,
        String namespace,
        String status,
        String ready,
        int restarts,
        String node,
        String age
) {
}
