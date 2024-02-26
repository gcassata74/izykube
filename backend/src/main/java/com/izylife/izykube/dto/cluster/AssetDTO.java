package com.izylife.izykube.dto.cluster;

import lombok.Data;

@Data
public class AssetDTO {
    private String id;
    private String name;
    private String description;
    private int port;
    private String image;
    private String version;
}
