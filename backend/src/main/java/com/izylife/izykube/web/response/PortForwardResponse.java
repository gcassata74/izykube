package com.izylife.izykube.web.response;

public class PortForwardResponse {
    private String namespace;
    private String serviceName;
    private int localPort;
    private int targetPort;
    private boolean active;
    private String message;

    public PortForwardResponse() {
    }

    public PortForwardResponse(String namespace, String serviceName, int localPort, int targetPort, boolean active, String message) {
        this.namespace = namespace;
        this.serviceName = serviceName;
        this.localPort = localPort;
        this.targetPort = targetPort;
        this.active = active;
        this.message = message;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
