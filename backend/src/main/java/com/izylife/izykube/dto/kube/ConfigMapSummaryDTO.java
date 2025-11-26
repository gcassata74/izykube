package com.izylife.izykube.dto.kube;

public record ConfigMapSummaryDTO(
        String name,
        String namespace,
        int dataEntries,
        String age
) {
}
