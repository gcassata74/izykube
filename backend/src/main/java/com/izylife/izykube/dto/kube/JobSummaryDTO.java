package com.izylife.izykube.dto.kube;

public record JobSummaryDTO(
        String name,
        String namespace,
        Integer completions,
        Integer succeeded,
        Integer failed,
        Integer active,
        String age
) {
}
