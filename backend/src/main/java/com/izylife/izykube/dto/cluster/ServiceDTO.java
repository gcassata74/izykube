package com.izylife.izykube.dto.cluster;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDTO extends NodeDTO {
    private String type;
    private int port;
    private Integer nodePort;
    private boolean exposeService;
    private String frontendUrl;

    public ServiceDTO(String id, String name, String type, int port) {
        super(id, name, "service");
        this.type = type;
        this.port = port;
        this.exposeService = false;
    }

    public ServiceDTO(String id, String name, String type, int port, Integer nodePort) {
        super(id, name, "service");
        this.type = type;
        this.port = port;
        this.nodePort = nodePort;
        this.exposeService = false;
    }

    public ServiceDTO(String id, String name, String type, int port, Integer nodePort, boolean exposeService, String frontendUrl) {
        super(id, name, "service");
        this.type = type;
        this.port = port;
        this.nodePort = nodePort;
        this.exposeService = exposeService;
        this.frontendUrl = frontendUrl;
    }
}