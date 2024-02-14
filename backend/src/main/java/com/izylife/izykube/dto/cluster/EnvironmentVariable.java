package com.izylife.izykube.dto.cluster;

import io.fabric8.kubernetes.api.model.EnvVarSource;
import lombok.Data;

@Data
public class EnvironmentVariable {
    private String name;
    private String value; // Optional
    private EnvVarSource valueFrom; // Optional
}