package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.dto.cluster.PodDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Processor(PodDTO.class)
@Service
public class PodProcessor implements TemplateProcessor<PodDTO> {

    private final AssetRepository assetRepository;

    @Autowired
    public PodProcessor(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Override
    public String createTemplate(PodDTO dto) {
        List<Container> containers = createContainers(dto);

        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withContainers(containers)
                .withRestartPolicy(dto.getRestartPolicy())
                .withServiceAccountName(dto.getServiceAccountName())
                .withNodeSelector(dto.getNodeSelector())
                .withHostNetwork(dto.getHostNetwork())
                .withDnsPolicy(dto.getDnsPolicy())
                .withSchedulerName(dto.getSchedulerName())
                .withPriority(dto.getPriority())
                .withPreemptionPolicy(dto.getPreemptionPolicy())
                .endSpec()
                .build();

        return Serialization.asYaml(pod);
    }

    private List<Container> createContainers(PodDTO dto) {
        return dto.getLinkedNodes().stream()
                .filter(node -> node instanceof ContainerDTO)
                .map(node -> (ContainerDTO) node)
                .map(this::createContainer)
                .collect(Collectors.toList());
    }

    private Container createContainer(ContainerDTO containerDTO) {
        Asset asset = assetRepository.findById(containerDTO.getAssetId())
                .orElseThrow(() -> new IllegalArgumentException("Asset not found for id: " + containerDTO.getAssetId()));

        ContainerPort containerPort = new ContainerPort();
        containerPort.setContainerPort(containerDTO.getContainerPort());

        return new ContainerBuilder()
                .withName(containerDTO.getName())
                .withImage(asset.getImage())
                .withPorts(containerPort)
                .build();
    }
}