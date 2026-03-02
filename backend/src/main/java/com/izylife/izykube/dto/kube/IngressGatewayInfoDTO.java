package com.izylife.izykube.dto.kube;

public record IngressGatewayInfoDTO(
        String host,
        Integer httpPort,
        Integer httpsPort,
        boolean loadBalancer
) {
}
