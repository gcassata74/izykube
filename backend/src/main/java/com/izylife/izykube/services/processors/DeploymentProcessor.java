package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.*;
import com.izylife.izykube.utils.ConfigMapUtils;
import com.izylife.izykube.utils.VolumeUtils;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.RollingUpdateDeploymentBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.Collectors;

@Processor(DeploymentDTO.class)
@Service
@AllArgsConstructor
public class DeploymentProcessor implements TemplateProcessor<DeploymentDTO> {

    private final ContainerProcessor containerProcessor;

    @Override
    public String createTemplate(DeploymentDTO dto) {
        String namespace = dto.getNamespace() == null || dto.getNamespace().isBlank() ? "default" : dto.getNamespace();
        List<Container> containers = createContainers(dto);
        List<EnvFromSource> envFromSources = createEnvFromSources(dto);
        List<Volume> volumes = createVolumes(dto);
        HostAlias hostAlias = null;

        ServiceDTO serviceDTO = dto.getSourceNodes().stream()
                .filter(ServiceDTO.class::isInstance)
                .map(ServiceDTO.class::cast)
                .findFirst()
                .orElse(null);


        if (serviceDTO != null && serviceDTO.getFrontendUrl() != null && !serviceDTO.getFrontendUrl().isEmpty()) {
            InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
            hostAlias = new HostAlias();
            hostAlias.setIp(loopbackAddress.getHostAddress());
            hostAlias.setHostnames(List.of(stripHttpPrefix(serviceDTO.getFrontendUrl())));
        }

        Map<String, String> labels = new HashMap<>();
        labels.put("app", dto.getName());

        String strategyType = dto.getStrategyType() != null ? dto.getStrategyType() : "RollingUpdate";

        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace(namespace)
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
                .withType(strategyType)
                .endStrategy()
                .endSpec()
                .build();

        if ("RollingUpdate".equalsIgnoreCase(strategyType)) {
            deployment.getSpec().getStrategy().setRollingUpdate(
                    new RollingUpdateDeploymentBuilder()
                            .withMaxSurge(new IntOrString(1))
                            .withMaxUnavailable(new IntOrString(0))
                            .build()
            );
        }

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
        Stream<NodeDTO> linkedNodes = Stream.concat(
                safeStream(dto.getTargetNodes()),
                safeStream(dto.getSourceNodes())
        );

        return linkedNodes
                .map(this::toEnvFromCandidate)
                .flatMap(Optional::stream)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                EnvFromCandidate::key,
                                EnvFromCandidate::source,
                                (existing, ignored) -> existing,
                                java.util.LinkedHashMap::new
                        ),
                        map -> new ArrayList<>(map.values())
                ));
    }

    private List<Container> createContainers(DeploymentDTO dto) {
        List<VolumeMount> volumeMounts = dto.getTargetNodes().stream()
                .filter(node -> node instanceof VolumeDTO)
                .map(node -> (VolumeDTO) node)
                .map(VolumeUtils::createVolumeMount)
                .collect(Collectors.toList());

        List<Container> containers = new ArrayList<>();

        if (dto.getAssetId() == null || dto.getAssetId().isBlank()) {
            throw new IllegalArgumentException("Deployment " + dto.getName() + " must specify an asset/image");
        }

        containers.add(containerProcessor.buildPrimaryContainer(dto, volumeMounts));

        containers.addAll(dto.getTargetNodes().stream()
                .filter(node -> node instanceof ContainerDTO)
                .map(node -> (ContainerDTO) node)
                .map(containerDTO -> containerProcessor.processContainer(containerDTO, volumeMounts))
                .collect(Collectors.toList()));

        if (containers.isEmpty()) {
            throw new IllegalArgumentException("Deployment must define at least one container image");
        }

        return containers;
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

    private Stream<NodeDTO> safeStream(List<NodeDTO> nodes) {
        return nodes == null ? Stream.empty() : nodes.stream().filter(Objects::nonNull);
    }

    private Optional<EnvFromCandidate> toEnvFromCandidate(NodeDTO node) {
        if (node == null) {
            return Optional.empty();
        }

        if (node instanceof ConfigMapDTO configMap) {
            String name = normalizeName(configMap.getName());
            if (name.isEmpty()) {
                return Optional.empty();
            }
            boolean secret = configMap.isSecret() || "secret".equalsIgnoreCase(configMap.getKind());
            EnvFromSource source = ConfigMapUtils.createEnvFromSource(name, secret);
            return Optional.of(new EnvFromCandidate(buildEnvFromKey(secret, name), source));
        }

        String kind = normalizeKind(node.getKind());
        if (!"configmap".equals(kind) && !"secret".equals(kind)) {
            return Optional.empty();
        }

        String name = normalizeName(node.getName());
        if (name.isEmpty()) {
            return Optional.empty();
        }
        boolean secret = "secret".equals(kind);
        EnvFromSource source = ConfigMapUtils.createEnvFromSource(name, secret);
        return Optional.of(new EnvFromCandidate(buildEnvFromKey(secret, name), source));
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private String normalizeKind(String kind) {
        return kind == null ? "" : kind.trim().toLowerCase();
    }

    private String buildEnvFromKey(boolean secret, String name) {
        return (secret ? "secret:" : "configmap:") + name;
    }

    private record EnvFromCandidate(String key, EnvFromSource source) { }
}
