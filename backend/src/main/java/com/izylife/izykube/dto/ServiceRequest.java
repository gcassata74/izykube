package com.izylife.izykube.dto;

import lombok.Data;

@Data
public class ServiceRequest {
    private String serviceName;
    private String deploymentName;
    private int port;
    private int targetPort;
    private String namespace;
}