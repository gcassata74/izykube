package com.izylife.izykube.dto.cluster;

import lombok.Data;

@Data
public class Asset {
    private String id;
    private String label;
    private int port;
    private String image;
    private String type;
    private String version;
}
