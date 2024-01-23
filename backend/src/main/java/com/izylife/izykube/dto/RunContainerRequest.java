package com.izylife.izykube.dto;

import lombok.Data;

@Data
public class RunContainerRequest {
    private String imageName;
    private String command;
    private String[] volumeBindings;
}
