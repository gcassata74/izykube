package com.izylife.izykube.dto.kube;

public record DaemonSetSummaryDTO(
        String name,
        String namespace,
        Integer desired,
        Integer current,
        Integer ready,
        Integer available,
        Integer updated,
        String age
) {
}
