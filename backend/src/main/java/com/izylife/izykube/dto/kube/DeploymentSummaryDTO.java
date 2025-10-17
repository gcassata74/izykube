package com.izylife.izykube.dto.kube;

public record DeploymentSummaryDTO(
        String name,
        String namespace,
        int readyReplicas,
        int replicas,
        int updatedReplicas,
        int availableReplicas,
        String age
) {
}
