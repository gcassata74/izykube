package com.izylife.izykube.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ServiceInfo {
    private String name;
    private Map<String, String> paths;  // Key = source path, Value = target path
    private int port;
}
