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
