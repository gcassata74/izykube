package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.*;
import com.izylife.izykube.repositories.AssetRepository;
import com.izylife.izykube.utils.ConfigMapUtils;
import com.izylife.izykube.utils.VolumeUtils;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Processor(DeploymentDTO.class)
@Service
@AllArgsConstructor
public class DeploymentProcessor implements TemplateProcessor<DeploymentDTO> {

    private final AssetRepository assetRepository;
    private final ContainerProcessor containerProcessor;

    @Override
    public String createTemplate(DeploymentDTO dto) {
        List<Container> containers = createContainers(dto);
        List<EnvFromSource> envFromSources = createEnvFromSources(dto);
        List<Volume> volumes = createVolumes(dto);
        HostAlias hostAlias = null;

        ServiceDTO serviceDTO = dto.getSourceNodes().stream()
                .filter(ServiceDTO.class::isInstance)
                .map(ServiceDTO.class::cast)
                .findFirst()
                .orElse(null);


        if (serviceDTO != null && !serviceDTO.getFrontendUrl().isEmpty()) {
            InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
            hostAlias = new HostAlias();
            hostAlias.setIp(loopbackAddress.getHostAddress());
            hostAlias.setHostnames(List.of(stripHttpPrefix(serviceDTO.getFrontendUrl())));
        }

        if (containers.isEmpty()) {
            throw new IllegalArgumentException("Deployment must have at least one linked Container");
        }

        Map<String, String> labels = new HashMap<>();
        labels.put("app", dto.getName());

        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withReplicas(dto.getReplicas())
                .withNewSelector()
                .withMatchLabels(labels)
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withContainers(containers)
                .withRestartPolicy("Always")
                .endSpec()
                .endTemplate()
                .withNewStrategy()
                .withType("RollingUpdate")
                .withNewRollingUpdate()
                .withMaxSurge(new IntOrString(1))
                .withMaxUnavailable(new IntOrString(0))
                .endRollingUpdate()
                .endStrategy()
                .endSpec()
                .build();

        deployment.getSpec().getTemplate().getSpec().getContainers()
                .forEach(container -> container.setEnvFrom(envFromSources));

        if (!volumes.isEmpty()) {
            deployment.getSpec().getTemplate().getSpec().setVolumes(volumes);
        }

        if (hostAlias != null) {
            deployment.getSpec().getTemplate().getSpec().setHostAliases(List.of(hostAlias));
        }

        return Serialization.asYaml(deployment);
    }

    private List<EnvFromSource> createEnvFromSources(DeploymentDTO dto) {
        return dto.getTargetNodes().stream()
                .filter(node -> node instanceof ConfigMapDTO)
                .map(node -> (ConfigMapDTO) node)
                .map(ConfigMapUtils::createEnvFromSource)
                .collect(Collectors.toList());
    }

    private List<Container> createContainers(DeploymentDTO dto) {
        List<VolumeMount> volumeMounts = dto.getTargetNodes().stream()
                .filter(node -> node instanceof VolumeDTO)
                .map(node -> (VolumeDTO) node)
                .map(VolumeUtils::createVolumeMount)
                .collect(Collectors.toList());

        return dto.getTargetNodes().stream()
                .filter(node -> node instanceof ContainerDTO)
                .map(node -> (ContainerDTO) node)
                .map(containerDTO -> containerProcessor.processContainer(containerDTO, volumeMounts))
                .collect(Collectors.toList());
    }

    private List<Volume> createVolumes(DeploymentDTO dto) {
        return dto.getTargetNodes().stream()
                .filter(node -> node instanceof VolumeDTO)
                .map(node -> (VolumeDTO) node)
                .map(VolumeUtils::createVolume)
                .collect(Collectors.toList());
    }

    private String stripHttpPrefix(String url) {
        return url.replaceAll("^(http://|https://)", "");
    }
}