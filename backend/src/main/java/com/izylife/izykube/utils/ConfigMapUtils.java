package com.izylife.izykube.utils;

import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;

public class ConfigMapUtils {
    public static EnvFromSource createEnvFromSource(ConfigMapDTO configMap) {
        EnvFromSourceBuilder builder = new EnvFromSourceBuilder();
        if (configMap.isSecret()) {
            builder.withNewSecretRef()
                    .withName(configMap.getName())
                    .endSecretRef();
        } else {
            builder.withNewConfigMapRef()
                    .withName(configMap.getName())
                    .endConfigMapRef();
        }
        return builder.build();
    }
}
