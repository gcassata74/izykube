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

import com.izylife.izykube.dto.cluster.VolumeDTO;
import io.fabric8.kubernetes.api.model.*;

public class VolumeUtils {

    public static Volume createVolume(VolumeDTO volumeDTO) {
        VolumeBuilder volumeBuilder = new VolumeBuilder()
                .withName(volumeDTO.getName());

        switch (volumeDTO.getType()) {
            case "emptyDir":
                volumeBuilder.withNewEmptyDir()
                        .withMedium((String) volumeDTO.getConfig().get("medium"))
                        .withSizeLimit(new Quantity((String) volumeDTO.getConfig().get("sizeLimit")))
                        .endEmptyDir();
                break;
            case "hostPath":
                volumeBuilder.withNewHostPath()
                        .withPath((String) volumeDTO.getConfig().get("path"))
                        .withType((String) volumeDTO.getConfig().get("hostPathType"))
                        .endHostPath();
                break;
            case "configMap":
                volumeBuilder.withNewConfigMap()
                        .withName((String) volumeDTO.getConfig().get("name"))
                        .endConfigMap();
                break;
            case "secret":
                volumeBuilder.withNewSecret()
                        .withSecretName((String) volumeDTO.getConfig().get("secretName"))
                        .endSecret();
                break;
            case "persistentVolumeClaim":
                volumeBuilder.withNewPersistentVolumeClaim()
                        .withClaimName((String) volumeDTO.getConfig().get("claimName"))
                        .withReadOnly((Boolean) volumeDTO.getConfig().get("readOnly"))
                        .endPersistentVolumeClaim();
                break;
            default:
                throw new IllegalArgumentException("Unsupported volume type: " + volumeDTO.getType());
        }

        return volumeBuilder.build();
    }

    public static VolumeMount createVolumeMount(VolumeDTO volumeDTO) {
        return new VolumeMountBuilder()
                .withName(volumeDTO.getName())
                .withMountPath((String) volumeDTO.getConfig().get("mountPath"))
                .withReadOnly(volumeDTO.getType().equals("persistentVolumeClaim")
                        && volumeDTO.getConfig().containsKey("readOnly")
                        ? (Boolean) volumeDTO.getConfig().get("readOnly")
                        : null)
                .build();
    }
}
