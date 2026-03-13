/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
