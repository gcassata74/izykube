package com.izylife.izykube.services;

import com.izylife.izykube.dto.kube.DeploymentLogsDTO;
import com.izylife.izykube.dto.kube.DeploymentSummaryDTO;
import com.izylife.izykube.dto.kube.NamespaceDTO;
import com.izylife.izykube.dto.kube.NamespaceSummaryDTO;
import com.izylife.izykube.dto.kube.PodLogDTO;
import com.izylife.izykube.dto.kube.PodSummaryDTO;
import com.izylife.izykube.dto.kube.ServiceSummaryDTO;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KubernetesExplorerService {

    private static final String ALL_NAMESPACES = "all";
    private static final int DEFAULT_TAIL_LINES = 500;
    private static final int MAX_TAIL_LINES = 2000;

    private final KubernetesClient kubernetesClient;

    public List<NamespaceDTO> listNamespaces() {
        return kubernetesClient.namespaces()
                .list()
                .getItems()
                .stream()
                .map(Namespace::getMetadata)
                .filter(metadata -> metadata != null && StringUtils.hasText(metadata.getName()))
                .map(metadata -> new NamespaceDTO(metadata.getName()))
                .sorted(Comparator.comparing(NamespaceDTO::name))
                .toList();
    }

    public NamespaceSummaryDTO getNamespaceSummary(String namespace) {
        boolean includeAll = !StringUtils.hasText(namespace) || ALL_NAMESPACES.equalsIgnoreCase(namespace);
        String effectiveNamespace = includeAll ? ALL_NAMESPACES : namespace;

        List<PodSummaryDTO> pods = (includeAll ? kubernetesClient.pods().inAnyNamespace() : kubernetesClient.pods().inNamespace(namespace))
                .list()
                .getItems()
                .stream()
                .map(this::mapPod)
                .sorted(Comparator.comparing(PodSummaryDTO::namespace).thenComparing(PodSummaryDTO::name))
                .toList();

        List<DeploymentSummaryDTO> deployments = (includeAll ? kubernetesClient.apps().deployments().inAnyNamespace() : kubernetesClient.apps().deployments().inNamespace(namespace))
                .list()
                .getItems()
                .stream()
                .map(this::mapDeployment)
                .sorted(Comparator.comparing(DeploymentSummaryDTO::namespace).thenComparing(DeploymentSummaryDTO::name))
                .toList();

        List<ServiceSummaryDTO> services = (includeAll ? kubernetesClient.services().inAnyNamespace() : kubernetesClient.services().inNamespace(namespace))
                .list()
                .getItems()
                .stream()
                .map(this::mapService)
                .sorted(Comparator.comparing(ServiceSummaryDTO::namespace).thenComparing(ServiceSummaryDTO::name))
                .toList();

        return new NamespaceSummaryDTO(effectiveNamespace, pods, deployments, services);
    }

    public PodLogDTO getPodLogs(String namespace, String podName, int tailLines) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(podName)) {
            return null;
        }
        PodResource podResource = kubernetesClient.pods().inNamespace(namespace).withName(podName);
        Pod pod = podResource.get();
        if (pod == null) {
            return null;
        }
        String logs = readPodLog(podResource, sanitizeTail(tailLines));
        return new PodLogDTO(podName, namespace, logs);
    }

    public DeploymentLogsDTO getDeploymentLogs(String namespace, String deploymentName, int tailLines) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(deploymentName)) {
            return null;
        }
        Deployment deployment = kubernetesClient.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
        if (deployment == null) {
            return null;
        }

        Map<String, String> selectorLabels = Optional.ofNullable(deployment.getSpec())
                .map(spec -> spec.getSelector())
                .map(sel -> sel.getMatchLabels())
                .orElseGet(Collections::emptyMap);

        Map<String, String> labelsToUse = selectorLabels.isEmpty()
                ? Map.of("app", deploymentName)
                : selectorLabels;

        List<Pod> pods = kubernetesClient.pods()
                .inNamespace(namespace)
                .withLabels(labelsToUse)
                .list()
                .getItems();

        int tail = sanitizeTail(tailLines);
        List<PodLogDTO> podLogs = pods.stream()
                .map(pod -> {
                    PodResource podResource = kubernetesClient.pods().inNamespace(namespace).withName(pod.getMetadata().getName());
                    String logContent = readPodLog(podResource, tail);
                    return new PodLogDTO(pod.getMetadata().getName(), namespace, logContent);
                })
                .toList();

        return new DeploymentLogsDTO(deploymentName, namespace, podLogs);
    }

    private PodSummaryDTO mapPod(Pod pod) {
        String name = Optional.ofNullable(pod.getMetadata()).map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "").orElse("");
        String namespace = Optional.ofNullable(pod.getMetadata()).map(meta -> StringUtils.hasText(meta.getNamespace()) ? meta.getNamespace() : "").orElse("");
        String status = Optional.ofNullable(pod.getStatus()).map(st -> StringUtils.hasText(st.getPhase()) ? st.getPhase() : "").orElse("");

        List<io.fabric8.kubernetes.api.model.ContainerStatus> containerStatuses = Optional.ofNullable(pod.getStatus())
                .map(io.fabric8.kubernetes.api.model.PodStatus::getContainerStatuses)
                .orElse(List.of());

        long readyCount = containerStatuses.stream().filter(io.fabric8.kubernetes.api.model.ContainerStatus::getReady).count();
        int totalContainers = containerStatuses.size();
        int restarts = containerStatuses.stream().mapToInt(cs -> Optional.ofNullable(cs.getRestartCount()).orElse(0)).sum();

        String ready = totalContainers > 0 ? readyCount + "/" + totalContainers : "0/0";
        String node = Optional.ofNullable(pod.getSpec()).map(spec -> StringUtils.hasText(spec.getNodeName()) ? spec.getNodeName() : "").orElse("");
        String age = formatAge(pod);

        return new PodSummaryDTO(name, namespace, status, ready, restarts, node, age);
    }

    private DeploymentSummaryDTO mapDeployment(Deployment deployment) {
        String name = Optional.ofNullable(deployment.getMetadata()).map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "").orElse("");
        String namespace = Optional.ofNullable(deployment.getMetadata()).map(meta -> StringUtils.hasText(meta.getNamespace()) ? meta.getNamespace() : "").orElse("");

        var status = Optional.ofNullable(deployment.getStatus());
        int readyReplicas = status.map(s -> Optional.ofNullable(s.getReadyReplicas()).orElse(0)).orElse(0);
        int replicas = Optional.ofNullable(deployment.getSpec()).map(spec -> Optional.ofNullable(spec.getReplicas()).orElse(0)).orElse(0);
        int updatedReplicas = status.map(s -> Optional.ofNullable(s.getUpdatedReplicas()).orElse(0)).orElse(0);
        int availableReplicas = status.map(s -> Optional.ofNullable(s.getAvailableReplicas()).orElse(0)).orElse(0);
        String age = formatAge(deployment);

        return new DeploymentSummaryDTO(name, namespace, readyReplicas, replicas, updatedReplicas, availableReplicas, age);
    }

    private ServiceSummaryDTO mapService(io.fabric8.kubernetes.api.model.Service service) {
        String name = Optional.ofNullable(service.getMetadata()).map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "").orElse("");
        String namespace = Optional.ofNullable(service.getMetadata()).map(meta -> StringUtils.hasText(meta.getNamespace()) ? meta.getNamespace() : "").orElse("");
        String type = Optional.ofNullable(service.getSpec()).map(spec -> StringUtils.hasText(spec.getType()) ? spec.getType() : "").orElse("");
        String clusterIp = Optional.ofNullable(service.getSpec()).map(spec -> StringUtils.hasText(spec.getClusterIP()) ? spec.getClusterIP() : "").orElse("");

        String externalIp = Optional.ofNullable(service.getStatus())
                .map(status -> status.getLoadBalancer())
                .map(lb -> lb.getIngress())
                .filter(ingress -> !CollectionUtils.isEmpty(ingress))
                .map(ingress -> ingress.stream()
                        .map(entry -> StringUtils.hasText(entry.getIp()) ? entry.getIp() : entry.getHostname())
                        .filter(StringUtils::hasText)
                        .collect(Collectors.joining(", ")))
                .filter(StringUtils::hasText)
                .orElseGet(() -> {
                    List<String> externalIps = Optional.ofNullable(service.getSpec())
                            .map(spec -> spec.getExternalIPs())
                            .orElse(List.of());
                    return externalIps.stream().filter(StringUtils::hasText).collect(Collectors.joining(", "));
                });

        String ports = Optional.ofNullable(service.getSpec())
                .map(spec -> spec.getPorts())
                .orElse(List.of())
                .stream()
                .map(port -> {
                    String protocol = StringUtils.hasText(port.getProtocol()) ? port.getProtocol() : "";
                    return port.getPort() + (StringUtils.hasText(protocol) ? "/" + protocol : "");
                })
                .collect(Collectors.joining(", "));

        String age = formatAge(service);

        return new ServiceSummaryDTO(name, namespace, type, clusterIp, externalIp, ports, age);
    }

    private String formatAge(HasMetadata resource) {
        String timestamp = Optional.ofNullable(resource.getMetadata()).map(meta -> meta.getCreationTimestamp()).orElse(null);
        if (!StringUtils.hasText(timestamp)) {
            return "-";
        }

        try {
            OffsetDateTime creationTime = OffsetDateTime.parse(timestamp);
            Duration duration = Duration.between(creationTime, OffsetDateTime.now());

            long days = duration.toDays();
            if (days > 0) {
                return days + "d";
            }

            long hours = duration.toHours();
            if (hours > 0) {
                return hours + "h";
            }

            long minutes = duration.toMinutes();
            if (minutes > 0) {
                return minutes + "m";
            }

            long seconds = duration.getSeconds();
            return Math.max(seconds, 0) + "s";
        } catch (DateTimeParseException exception) {
            log.warn("Unable to parse Kubernetes creation timestamp: {}", timestamp, exception);
            return "-";
        }
    }

    private String readPodLog(PodResource podResource, int tailLines) {
        try {
            return Optional.ofNullable(podResource.tailingLines(tailLines).getLog()).orElse("");
        } catch (Exception ex) {
            log.warn("Failed to read pod logs: {}", ex.getMessage());
            return "Unable to retrieve logs: " + ex.getMessage();
        }
    }

    private int sanitizeTail(int tailLines) {
        if (tailLines <= 0) {
            return DEFAULT_TAIL_LINES;
        }
        return Math.min(tailLines, MAX_TAIL_LINES);
    }
}
