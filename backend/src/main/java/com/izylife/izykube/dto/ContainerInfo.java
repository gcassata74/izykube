package com.izylife.izykube.dto;

import lombok.Data;

@Data
public class ContainerInfo {
    private String id;
    private String image;
    private String name;

    public ContainerInfo(String id, String image, String name) {
        this.id = id;
        this.image = image;
        this.name = name;
    }
}

