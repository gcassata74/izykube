package com.izylife.izykube.dto.kube;

public record SecretSummaryDTO(
        String name,
        String namespace,
        String type,
        int dataEntries,
        String age
) {
}
