package com.izylife.izykube.dto.kube;

public record IstioGatewayInfoDTO(
        String host,
        Integer httpPort,
        Integer httpsPort,
        boolean loadBalancer
) {
}
