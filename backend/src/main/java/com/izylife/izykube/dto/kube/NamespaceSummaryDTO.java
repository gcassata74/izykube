package com.izylife.izykube.dto.kube;

import java.util.List;

public record NamespaceSummaryDTO(
        String namespace,
        List<PodSummaryDTO> pods,
        List<DeploymentSummaryDTO> deployments,
        List<ServiceSummaryDTO> services
) {
}
