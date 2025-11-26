package com.izylife.izykube.dto.kube;

public record StatefulSetSummaryDTO(
        String name,
        String namespace,
        Integer readyReplicas,
        Integer replicas,
        Integer updatedReplicas,
        String age
) {
}
