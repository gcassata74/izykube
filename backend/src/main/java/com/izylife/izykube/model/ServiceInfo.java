package com.izylife.izykube.model;

import lombok.Data;

@Data
public class ServiceInfo {
    private String name;
    private String host;
    private String path;
    private int port;
}

