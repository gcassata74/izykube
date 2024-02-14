package com.izylife.izykube.dto.cluster;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Service {

    // Assuming Base properties are id and name, directly included in Service DTO
    private String id;
    private String name;
    private ServiceType type; // Using enum for ServiceType
    private List<ServicePort> ports;
    private Map<String, String> selector;

    // Enum for ServiceType to ensure values are limited to 'ClusterIP', 'NodePort', or 'LoadBalancer'
    public enum ServiceType {
        ClusterIP, NodePort, LoadBalancer
    }

    @Data
    public static class ServicePort {
        private int port;
        private int targetPort;
        private String protocol; // Consider using an enum for 'TCP' and 'UDP' if applicable
    }
}
