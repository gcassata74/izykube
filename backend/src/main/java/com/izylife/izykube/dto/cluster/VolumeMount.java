package com.izylife.izykube.dto.cluster;

import lombok.Data;

@Data
public class VolumeMount {
    private String name;
    private String mountPath;
}