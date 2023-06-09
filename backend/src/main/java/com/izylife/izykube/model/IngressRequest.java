package com.izylife.izykube.model;

import lombok.Data;
import java.util.List;

@Data
public class IngressRequest {
    private String name;
    private List<ServiceInfo> services;
}