package com.izylife.izykube.web.response;

public class PortAvailabilityResponse {
    private int port;
    private boolean available;
    private String message;

    public PortAvailabilityResponse() {
    }

    public PortAvailabilityResponse(int port, boolean available, String message) {
        this.port = port;
        this.available = available;
        this.message = message;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
