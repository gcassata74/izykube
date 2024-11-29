package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Processor(ContainerDTO.class)
@Service
@AllArgsConstructor
public class ContainerProcessor implements TemplateProcessor<ContainerDTO> {

    private final AssetRepository assetRepository;

    @Override
    public String createTemplate(ContainerDTO dto) {
        Container container = processContainer(dto, List.of());
        return io.fabric8.kubernetes.client.utils.Serialization.asYaml(container);
    }

    public Container processContainer(ContainerDTO dto, List<VolumeMount> volumeMounts) {
        Asset asset = assetRepository.findById(dto.getAssetId())
                .orElseThrow(() -> new IllegalArgumentException("Asset not found for id: " + dto.getAssetId()));

        return new ContainerBuilder()
                .withName(dto.getName())
                .withImage(asset.getImage())
                .withVolumeMounts(volumeMounts)
                .addNewPort()
                .withContainerPort(dto.getContainerPort())
                .endPort()
                .build();
    }
}
