package com.izylife.izykube.utils;

import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;

public class ConfigMapUtils {
    public static EnvFromSource createEnvFromSource(ConfigMapDTO configMap) {
        return new EnvFromSourceBuilder()
                .withNewConfigMapRef()
                .withName(configMap.getName())
                .endConfigMapRef()
                .build();
    }
}
