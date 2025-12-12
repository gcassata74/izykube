package com.izylife.izykube.utils;

import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;

public class ConfigMapUtils {
    public static EnvFromSource createEnvFromSource(ConfigMapDTO configMap) {
        if (configMap == null) {
            throw new IllegalArgumentException("ConfigMapDTO cannot be null");
        }
        String name = requireName(configMap.getName());
        boolean secret = "secret".equalsIgnoreCase(configMap.getKind());
        return createEnvFromSource(name, secret);
    }

    public static EnvFromSource createEnvFromSource(String name, boolean secret) {
        String normalizedName = requireName(name);
        EnvFromSourceBuilder builder = new EnvFromSourceBuilder();
        if (secret) {
            builder.withNewSecretRef()
                    .withName(normalizedName)
                    .endSecretRef();
        } else {
            builder.withNewConfigMapRef()
                    .withName(normalizedName)
                    .endConfigMapRef();
        }
        return builder.build();
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Config or secret name cannot be null or blank");
        }
        return name.trim();
    }
}
