package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Processor(ContainerDTO.class)
@Service
public class ContainerProcessor implements TemplateProcessor<ContainerDTO> {

    private final AssetRepository assetRepository;

    @Autowired
    public ContainerProcessor(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Override
    public String createTemplate(ContainerDTO dto) {
        Asset asset = assetRepository.findById(dto.getAssetId())
                .orElseThrow(() -> new IllegalArgumentException("Asset not found for id: " + dto.getAssetId()));

        ContainerPort containerPort = new ContainerPort();
        containerPort.setContainerPort(dto.getContainerPort());

        Container container = new ContainerBuilder()
                .withName(dto.getName())
                .withImage(asset.getImage())
                .withPorts(Collections.singletonList(containerPort))
                .build();

        return Serialization.asYaml(container);
    }
}