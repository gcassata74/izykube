package com.izylife.izykube.utils;

import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.model.Asset;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;

import java.util.List;
import java.util.UUID;

public class ContainerUtils {
    public static Container createContainer(ContainerDTO containerDTO, Asset asset, List<VolumeMount> volumeMounts) {

        String sidecarSuffix = containerDTO.isSidecar() ? "-sidecar-" + UUID.randomUUID().toString() : "";
        Container container = new ContainerBuilder()
                .withName(containerDTO.getName() + sidecarSuffix)
                .withImage(asset.getImage())
                .addNewPort()
                .withContainerPort(containerDTO.getContainerPort())
                .endPort()
                .build();

        if (!volumeMounts.isEmpty()) {
            container.setVolumeMounts(volumeMounts);
        }
        return container;
    }
}
