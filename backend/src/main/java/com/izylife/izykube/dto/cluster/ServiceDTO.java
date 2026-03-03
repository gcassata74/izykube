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
    private boolean forwardEnabled;
    private Integer forwardPort;
    private Integer forwardTargetPort;
    private boolean forwardActive;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Integer getNodePort() {
        return nodePort;
    }

    public void setNodePort(Integer nodePort) {
        this.nodePort = nodePort;
    }

    public boolean isExposeService() {
        return exposeService;
    }

    public void setExposeService(boolean exposeService) {
        this.exposeService = exposeService;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }


    public boolean isForwardEnabled() {
        return forwardEnabled;
    }

    public void setForwardEnabled(boolean forwardEnabled) {
        this.forwardEnabled = forwardEnabled;
    }

    public Integer getForwardPort() {
        return forwardPort;
    }

    public void setForwardPort(Integer forwardPort) {
        this.forwardPort = forwardPort;
    }

    public Integer getForwardTargetPort() {
        return forwardTargetPort;
    }

    public void setForwardTargetPort(Integer forwardTargetPort) {
        this.forwardTargetPort = forwardTargetPort;
    }

    public boolean isForwardActive() {
        return forwardActive;
    }

    public void setForwardActive(boolean forwardActive) {
        this.forwardActive = forwardActive;
    }
}
