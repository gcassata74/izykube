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

package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.*;
import com.izylife.izykube.utils.ConfigMapUtils;
import com.izylife.izykube.utils.VolumeUtils;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.HostAlias;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.RollingUpdateDeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.izylife.izykube.dto.cluster.ContainerRole;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import com.izylife.izykube.dto.cluster.LinkDTO;

@Processor(DeploymentDTO.class)
@Service
@AllArgsConstructor
@Slf4j
public class DeploymentProcessor implements TemplateProcessor<DeploymentDTO> {

    private final ContainerProcessor containerProcessor;

    @Override
    public String createTemplate(DeploymentDTO dto) {
        String namespace = dto.getNamespace() == null || dto.getNamespace().isBlank() ? "default" : dto.getNamespace();
        String workloadName = sanitizeName(dto.getName());
        if (workloadName.isEmpty()) {
            throw new IllegalArgumentException("Deployment name is required to generate templates");
        }

        ContainerGroups containerGroups = createContainers(dto, workloadName);
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
        labels.put("app", workloadName);

        PodSpecBuilder podSpecBuilder = new PodSpecBuilder().withRestartPolicy("Always");
        applyPodContainers(containerGroups, podSpecBuilder);
        if (!volumes.isEmpty()) {
            podSpecBuilder.withVolumes(volumes);
        }
        if (hostAlias != null) {
            podSpecBuilder.withHostAliases(List.of(hostAlias));
        }
        String serviceAccountName = resolveServiceAccountName(dto, namespace);
        if (StringUtils.hasText(serviceAccountName)) {
            podSpecBuilder.withServiceAccountName(serviceAccountName);
        }

        PodSpec podSpec = podSpecBuilder.build();
        podSpec.getContainers().forEach(container -> container.setEnvFrom(envFromSources));
        if (podSpec.getInitContainers() != null) {
            podSpec.getInitContainers().forEach(container -> container.setEnvFrom(envFromSources));
        }

        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder().withLabels(labels);
        metaBuilder.addToAnnotations("sidecar.istio.io/inject", dto.isAddToMesh() ? "true" : "false");
        if (dto.isAddToMesh()) {
            metaBuilder.addToLabels("sidecar.istio.io/inject", "true");
        }
        PodTemplateSpec podTemplate = new PodTemplateSpecBuilder()
                .withMetadata(metaBuilder.build())
                .withSpec(podSpec)
                .build();

        HasMetadata workload = switch (dto.resolveWorkloadType()) {
            case STATEFULSET -> buildStatefulSet(dto, namespace, workloadName, labels, podTemplate, serviceDTO);
            case DAEMONSET -> buildDaemonSet(workloadName, namespace, labels, podTemplate);
            default -> buildDeployment(dto, workloadName, namespace, labels, podTemplate);
        };

        return Serialization.asYaml(workload);
    }

    private String resolveServiceAccountName(DeploymentDTO dto, String workloadNamespace) {
        if (dto == null) {
            return null;
        }
        String directName = normalizeName(dto.getServiceAccountName());
        if (StringUtils.hasText(directName)) {
            validateDns1123Subdomain(directName);
            return directName;
        }
        List<LinkDTO> incomingBindings = Optional.ofNullable(dto.getIncomingLinks())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .filter(link -> "serviceAccountBinding".equalsIgnoreCase(link.getType()))
                .toList();
        if (incomingBindings.size() > 1) {
            throw new IllegalArgumentException("Workload " + dto.getName() + " references multiple ServiceAccounts; only one is allowed");
        }

        String ref = dto.getServiceAccountRef();
        ServiceAccountDTO serviceAccount = null;
        if (StringUtils.hasText(ref)) {
            serviceAccount = resolveServiceAccountById(dto, ref);
        } else {
            if (incomingBindings.size() == 1) {
                String sourceId = incomingBindings.get(0).getSource();
                if (StringUtils.hasText(sourceId)) {
                    dto.setServiceAccountRef(sourceId);
                    serviceAccount = resolveServiceAccountById(dto, sourceId);
                }
            }
        }

        if (serviceAccount != null && incomingBindings.size() == 1 && incomingBindings.get(0).getSource() != null) {
            String linkedId = incomingBindings.get(0).getSource();
            if (StringUtils.hasText(linkedId) && StringUtils.hasText(ref) && !linkedId.equals(ref)) {
                throw new IllegalArgumentException("Workload " + dto.getName() + " ServiceAccount reference does not match its diagram binding");
            }
        }

        if (serviceAccount == null) {
            return null;
        }

        String saNamespace = serviceAccount.getNamespace();
        String effectiveSaNamespace = saNamespace == null || saNamespace.isBlank() ? workloadNamespace : saNamespace;
        if (!Objects.equals(workloadNamespace, effectiveSaNamespace)) {
            throw new IllegalArgumentException("Workload namespace must match ServiceAccount namespace. Kubernetes does not allow using a ServiceAccount across namespaces.");
        }

        String name = normalizeName(serviceAccount.getName());
        if (name.isEmpty()) {
            throw new IllegalArgumentException("ServiceAccount name is required");
        }
        validateDns1123Subdomain(name);
        return name;
    }

    private ServiceAccountDTO resolveServiceAccountById(DeploymentDTO dto, String serviceAccountId) {
        if (dto == null || !StringUtils.hasText(serviceAccountId)) {
            return null;
        }
        Map<String, NodeDTO> nodeIndex = dto.getNodeIndex();
        if (nodeIndex == null) {
            throw new IllegalArgumentException("Workload " + dto.getName() + " cannot resolve ServiceAccount reference (node index missing)");
        }
        NodeDTO resolved = nodeIndex.get(serviceAccountId);
        if (resolved == null) {
            throw new IllegalArgumentException("Workload " + dto.getName() + " references missing ServiceAccount: " + serviceAccountId);
        }
        if (!(resolved instanceof ServiceAccountDTO serviceAccount)) {
            throw new IllegalArgumentException("Workload " + dto.getName() + " references non-ServiceAccount node: " + serviceAccountId);
        }
        return serviceAccount;
    }

    private void validateDns1123Subdomain(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ServiceAccount name is required");
        }
        if (name.length() > 253) {
            throw new IllegalArgumentException("ServiceAccount name must be <= 253 characters");
        }
        if (!name.matches("^[a-z0-9]([a-z0-9-.]*[a-z0-9])?$")) {
            throw new IllegalArgumentException("ServiceAccount name must be a valid DNS-1123 subdomain (lowercase alphanumeric, '-', '.', start/end alphanumeric)");
        }
    }

    private void applyPodContainers(ContainerGroups containerGroups, PodSpecBuilder podSpecBuilder) {
        if (containerGroups == null || podSpecBuilder == null) {
            return;
        }
        podSpecBuilder.withContainers(mergeContainers(containerGroups.mainContainers(), containerGroups.sidecars()));
        if (!containerGroups.initContainers().isEmpty()) {
            podSpecBuilder.withInitContainers(containerGroups.initContainers());
        }
    }

    private Deployment buildDeployment(DeploymentDTO dto, String workloadName, String namespace, Map<String, String> labels, PodTemplateSpec podTemplate) {
        String strategyType = dto.getStrategyType() != null ? dto.getStrategyType() : "RollingUpdate";
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(workloadName)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withReplicas(dto.getReplicas())
                .withNewSelector()
                .withMatchLabels(labels)
                .endSelector()
                .withTemplate(podTemplate)
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
        return deployment;
    }

    private StatefulSet buildStatefulSet(DeploymentDTO dto,
                                         String namespace,
                                         String workloadName,
                                         Map<String, String> labels,
                                         PodTemplateSpec podTemplate,
                                         ServiceDTO serviceDTO) {
        String serviceName = sanitizeName(serviceDTO != null ? serviceDTO.getName() : workloadName);
        serviceName = serviceName.isEmpty() ? workloadName : serviceName;

        return new StatefulSetBuilder()
                .withNewMetadata()
                .withName(workloadName)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withReplicas(dto.getReplicas())
                .withServiceName(serviceName)
                .withNewSelector().withMatchLabels(labels).endSelector()
                .withTemplate(podTemplate)
                .endSpec()
                .build();
    }

    private DaemonSet buildDaemonSet(String workloadName,
                                     String namespace,
                                     Map<String, String> labels,
                                     PodTemplateSpec podTemplate) {
        return new DaemonSetBuilder()
                .withNewMetadata()
                .withName(workloadName)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withNewSelector().withMatchLabels(labels).endSelector()
                .withTemplate(podTemplate)
                .endSpec()
                .build();
    }

    private List<EnvFromSource> createEnvFromSources(DeploymentDTO dto) {
        Map<String, BundleSensitivity> bundleSensitivityByName = new LinkedHashMap<>();
        List<EnvFromCandidate> candidates = new ArrayList<>();

        Stream.concat(safeStream(dto.getTargetNodes()), safeStream(dto.getSourceNodes()))
                .forEach(node -> {
                    if (node instanceof ConfigMapDTO configMap) {
                        String name = normalizeName(configMap.getName());
                        if (!name.isEmpty()) {
                            bundleSensitivityByName.merge(
                                    name,
                                    detectBundleSensitivity(configMap),
                                    BundleSensitivity::merge
                            );
                        }
                    }
                    candidates.addAll(toEnvFromCandidates(node));
                });

        Map<String, EnvFromSource> deduped = new LinkedHashMap<>();
        for (EnvFromCandidate candidate : candidates) {
            deduped.putIfAbsent(candidate.key(), candidate.source());
        }

        bundleSensitivityByName.forEach((name, sensitivity) -> {
            if (!sensitivity.hasSecretEntries()) {
                return;
            }
            if (sensitivity.hasPlainEntries()) {
                deduped.putIfAbsent(buildEnvFromKey(false, name), ConfigMapUtils.createEnvFromSource(name, false));
            }
            deduped.putIfAbsent(buildEnvFromKey(true, name), ConfigMapUtils.createEnvFromSource(name, true));
        });

        return new ArrayList<>(deduped.values());
    }

    private ContainerGroups createContainers(DeploymentDTO dto, String sanitizedName) {
        List<VolumeMount> volumeMounts = safeStream(dto.getTargetNodes())
                .filter(node -> node instanceof VolumeDTO)
                .map(node -> (VolumeDTO) node)
                .map(VolumeUtils::createVolumeMount)
                .collect(Collectors.toList());

        List<Container> mainContainers = new ArrayList<>();

        if (dto.getAssetId() == null || dto.getAssetId().isBlank()) {
            throw new IllegalArgumentException("Deployment " + dto.getName() + " must specify an asset/image");
        }

        Container primary = containerProcessor.buildPrimaryContainer(dto, volumeMounts);
        primary.setName(sanitizedName);
        mainContainers.add(primary);

        List<ContainerWithMetadata> attachedContainers = Stream.concat(
                        safeStream(dto.getTargetNodes()),
                        safeStream(dto.getSourceNodes()))
                .filter(node -> node instanceof ContainerDTO)
                .map(node -> (ContainerDTO) node)
                .collect(Collectors.toMap(
                        ContainerDTO::getId,
                        containerDTO -> buildAttachedContainer(dto, containerDTO, sanitizedName, volumeMounts),
                        (a, b) -> a,
                        LinkedHashMap::new))
                .values()
                .stream()
                .collect(Collectors.toList());

        validateUniqueContainerNames(dto, sanitizedName, mainContainers, attachedContainers);

        if (mainContainers.isEmpty()) {
            throw new IllegalArgumentException("Deployment must define at least one container image");
        }

        if (log.isDebugEnabled()) {
            log.debug("Classifying linked containers for workload {} (id={}): linkedContainerNodes={}",
                    sanitizedName,
                    dto.getId(),
                    attachedContainers.size());
            for (ContainerWithMetadata attached : attachedContainers) {
                ContainerDTO source = attached.source();
                LinkDTO link = attached.link();
                log.debug("Linked container: id={} name={} role={} linkRole={}",
                        source != null ? source.getId() : null,
                        source != null ? source.getName() : null,
                        attached.role(),
                        link != null ? link.getContainerRole() : null);
            }
        }

        List<Container> initContainers = attachedContainers.stream()
                .filter(item -> item.role() == ContainerRole.INIT)
                .map(ContainerWithMetadata::container)
                .sorted(this::compareByName)
                .collect(Collectors.toList());

        List<Container> sidecars = attachedContainers.stream()
                .filter(item -> item.role() != ContainerRole.INIT)
                .map(ContainerWithMetadata::container)
                .sorted(this::compareByName)
                .collect(Collectors.toList());

        if (log.isDebugEnabled()) {
            log.debug("Container classification result for workload {} (id={}): initContainers={}, containers={}",
                    sanitizedName,
                    dto.getId(),
                    initContainers.size(),
                    mainContainers.size() + sidecars.size());
        }

        return new ContainerGroups(mainContainers, sidecars, initContainers);
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

    private List<Container> mergeContainers(List<Container> mainContainers, List<Container> sidecars) {
        List<Container> merged = new ArrayList<>(Optional.ofNullable(mainContainers).orElse(List.of()));
        if (sidecars != null && !sidecars.isEmpty()) {
            merged.addAll(sidecars);
        }
        return merged;
    }

    private ContainerWithMetadata buildAttachedContainer(DeploymentDTO deployment,
                                                         ContainerDTO containerDTO,
                                                         String fallbackName,
                                                         List<VolumeMount> volumeMounts) {
        Container container = containerProcessor.processContainer(containerDTO, volumeMounts);
        String containerName = sanitizeName(container.getName());
        if (containerName.isEmpty()) {
            containerName = fallbackName;
        }
        container.setName(containerName);

        ContainerRole role = resolveContainerRole(deployment, containerDTO);
        LinkDTO link = findLinkTo(deployment, containerDTO.getId());
        return new ContainerWithMetadata(container, role, containerDTO, link);
    }

    private ContainerRole resolveContainerRole(DeploymentDTO deployment, ContainerDTO containerDTO) {
        if (containerDTO != null && containerDTO.getRole() != null) {
            return containerDTO.getRole();
        }
        ContainerRole roleFromLink = Optional.ofNullable(findLinkTo(deployment, containerDTO != null ? containerDTO.getId() : null))
                .map(LinkDTO::getContainerRole)
                .orElse(null);
        if (roleFromLink != null) {
            return roleFromLink;
        }
        return ContainerRole.SIDECAR;
    }

    private LinkDTO findLinkTo(DeploymentDTO deployment, String targetId) {
        if (deployment == null || targetId == null) {
            return null;
        }
        Stream<LinkDTO> outgoing = Optional.ofNullable(deployment.getOutgoingLinks()).stream().flatMap(List::stream);
        Stream<LinkDTO> incoming = Optional.ofNullable(deployment.getIncomingLinks()).stream().flatMap(List::stream);

        return Stream.concat(outgoing, incoming)
                .filter(Objects::nonNull)
                .filter(link -> targetId.equals(link.getTarget()) || targetId.equals(link.getSource()))
                .findFirst()
                .orElse(null);
    }

    private void validateUniqueContainerNames(DeploymentDTO dto,
                                              String mainName,
                                              List<Container> mainContainers,
                                              List<ContainerWithMetadata> attachedContainers) {
        Map<String, List<ContainerConflict>> conflictsByName = new LinkedHashMap<>();

        for (Container container : Optional.ofNullable(mainContainers).orElse(List.of())) {
            addConflict(conflictsByName,
                    container != null ? container.getName() : null,
                    new ContainerConflict("main", dto.getId(), null));
        }

        for (ContainerWithMetadata attached : attachedContainers) {
            addConflict(
                    conflictsByName,
                    attached.container().getName(),
                    new ContainerConflict(
                            attached.role().name().toLowerCase(),
                            attached.source().getId(),
                            attached.link() != null ? attached.link().getId() : null
                    )
            );
        }

        List<String> duplicates = conflictsByName.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .toList();

        if (duplicates.isEmpty()) {
            return;
        }

        String details = duplicates.stream()
                .map(name -> name + " -> " + conflictsByName.get(name).stream()
                        .map(conflict -> conflict.type() +
                                "(node:" + conflict.nodeId() +
                                (conflict.linkId() != null ? ", link:" + conflict.linkId() : "") + ")")
                        .collect(Collectors.joining(", ")))
                .collect(Collectors.joining("; "));

        throw new IllegalArgumentException(
                "Deployment " + dto.getName() + " (" + dto.getId() + ") has duplicate container name(s): "
                        + String.join(", ", duplicates) + ". Conflicts: " + details
        );
    }

    private void addConflict(Map<String, List<ContainerConflict>> conflictsByName, String name, ContainerConflict conflict) {
        if (name == null) {
            return;
        }
        conflictsByName.computeIfAbsent(name, key -> new ArrayList<>()).add(conflict);
    }

    private int compareByName(Container a, Container b) {
        String nameA = Optional.ofNullable(a.getName()).orElse("");
        String nameB = Optional.ofNullable(b.getName()).orElse("");
        return nameA.compareToIgnoreCase(nameB);
    }

    private record ContainerGroups(List<Container> mainContainers,
                                   List<Container> sidecars,
                                   List<Container> initContainers) {}

    private record ContainerWithMetadata(Container container,
                                         ContainerRole role,
                                         ContainerDTO source,
                                         LinkDTO link) {}

    private record ContainerConflict(String type, String nodeId, String linkId) {}

    private Stream<NodeDTO> safeStream(List<NodeDTO> nodes) {
        return nodes == null ? Stream.empty() : nodes.stream().filter(Objects::nonNull);
    }

    private List<EnvFromCandidate> toEnvFromCandidates(NodeDTO node) {
        List<EnvFromCandidate> candidates = new ArrayList<>();
        if (node == null) {
            return candidates;
        }

        if (node instanceof ConfigMapDTO configMap) {
            String name = normalizeName(configMap.getName());
            if (name.isEmpty()) {
                return candidates;
            }

            BundleSensitivity sensitivity = detectBundleSensitivity(configMap);
            boolean hasPlainEntries = sensitivity.hasPlainEntries();
            boolean hasSecretEntries = sensitivity.hasSecretEntries();

            boolean markedSecret = (node instanceof com.izylife.izykube.dto.cluster.SecretDTO) || isSecretConfig(configMap);
            boolean treatAsSecretOnly = markedSecret && !hasPlainEntries && !hasSecretEntries;

            if (hasPlainEntries) {
                EnvFromSource source = ConfigMapUtils.createEnvFromSource(name, false);
                candidates.add(new EnvFromCandidate(buildEnvFromKey(false, name), source));
            }

            if (hasSecretEntries || treatAsSecretOnly) {
                EnvFromSource source = ConfigMapUtils.createEnvFromSource(name, true);
                candidates.add(new EnvFromCandidate(buildEnvFromKey(true, name), source));
            }

            if (candidates.isEmpty()) {
                boolean secret = isSecretConfig(configMap);
                EnvFromSource source = ConfigMapUtils.createEnvFromSource(name, secret);
                candidates.add(new EnvFromCandidate(buildEnvFromKey(secret, name), source));
            }
            return candidates;
        }

        // Fallback: if a linked node is represented only by kind "secret", still mount as secretRef
        if ("secret".equalsIgnoreCase(node.getKind())) {
            String name = normalizeName(node.getName());
            if (name.isEmpty()) {
                return candidates;
            }
            EnvFromSource source = ConfigMapUtils.createEnvFromSource(name, true);
            candidates.add(new EnvFromCandidate(buildEnvFromKey(true, name), source));
            return candidates;
        }

        String kind = normalizeKind(node.getKind());
        if (!"configmap".equals(kind) && !"secret".equals(kind)) {
            return candidates;
        }

        String name = normalizeName(node.getName());
        if (name.isEmpty()) {
            return candidates;
        }
        boolean secret = "secret".equals(kind);
        EnvFromSource source = ConfigMapUtils.createEnvFromSource(name, secret);
        candidates.add(new EnvFromCandidate(buildEnvFromKey(secret, name), source));
        return candidates;
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private boolean isSecretConfig(ConfigMapDTO configMap) {
        if (configMap == null) {
            return false;
        }
        boolean markedSecret = "secret".equalsIgnoreCase(configMap.getKind())
                || (configMap.getId() != null && configMap.getId().toLowerCase().startsWith("secret:"));
        if (markedSecret) {
            return true;
        }
        return Optional.ofNullable(configMap.getEntries())
                .orElse(List.of())
                .stream()
                .anyMatch(entry -> entry != null && ConfigEntrySensitivity.SECRET.equals(entry.getSensitivity()));
    }

    private String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return normalized;
    }

    private String normalizeKind(String kind) {
        return kind == null ? "" : kind.trim().toLowerCase();
    }

    private BundleSensitivity detectBundleSensitivity(ConfigMapDTO configMap) {
        if (configMap == null) {
            return new BundleSensitivity(false, false);
        }
        boolean hasPlainEntries = Optional.ofNullable(configMap.getEntries())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .anyMatch(entry -> entry.getSensitivity() != ConfigEntrySensitivity.SECRET);
        boolean hasSecretEntries = Optional.ofNullable(configMap.getEntries())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .anyMatch(entry -> ConfigEntrySensitivity.SECRET.equals(entry.getSensitivity()));
        return new BundleSensitivity(hasPlainEntries, hasSecretEntries);
    }

    private String buildEnvFromKey(boolean secret, String name) {
        return (secret ? "secret:" : "configmap:") + name;
    }

    private record EnvFromCandidate(String key, EnvFromSource source) { }

    private record BundleSensitivity(boolean hasPlainEntries, boolean hasSecretEntries) {
        BundleSensitivity merge(BundleSensitivity other) {
            if (other == null) {
                return this;
            }
            return new BundleSensitivity(
                    this.hasPlainEntries || other.hasPlainEntries,
                    this.hasSecretEntries || other.hasSecretEntries
            );
        }
    }
}
