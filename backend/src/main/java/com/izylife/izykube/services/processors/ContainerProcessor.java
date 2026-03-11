package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

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

    public Container buildPrimaryContainer(DeploymentDTO deployment, List<VolumeMount> volumeMounts) {
        if (deployment.getAssetId() == null || deployment.getAssetId().isBlank()) {
            throw new IllegalArgumentException("Deployment " + deployment.getName() + " must specify an asset");
        }

        Asset asset = assetRepository.findById(deployment.getAssetId())
                .orElseThrow(() -> new IllegalArgumentException("Asset not found for deployment: " + deployment.getName()));

        int port = deployment.getContainerPort() != null && deployment.getContainerPort() > 0
                ? deployment.getContainerPort()
                : 80;

        ContainerBuilder builder = new ContainerBuilder()
                .withName(deployment.getName())
                .withImage(asset.getImage())
                .withVolumeMounts(volumeMounts)
                .addNewPort()
                .withContainerPort(port)
                .endPort()
                ;
        if (!CollectionUtils.isEmpty(deployment.getCommand())) {
            builder.withCommand(deployment.getCommand());
        }
        if (!CollectionUtils.isEmpty(deployment.getArgs())) {
            builder.withArgs(deployment.getArgs());
        }
        return builder.build();
    }
}
