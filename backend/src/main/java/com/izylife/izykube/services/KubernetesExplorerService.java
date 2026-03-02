package com.izylife.izykube.services;

import com.izylife.izykube.collections.ClusterStatusEnum;
import com.izylife.izykube.dto.kube.ConfigMapSummaryDTO;
import com.izylife.izykube.dto.kube.CronJobSummaryDTO;
import com.izylife.izykube.dto.kube.DaemonSetSummaryDTO;
import com.izylife.izykube.dto.kube.DeploymentLogsDTO;
import com.izylife.izykube.dto.kube.DeploymentSummaryDTO;
import com.izylife.izykube.dto.kube.IngressClassSummaryDTO;
import com.izylife.izykube.dto.kube.IngressGatewayInfoDTO;
import com.izylife.izykube.dto.kube.IngressSummaryDTO;
import com.izylife.izykube.dto.kube.WorkloadHealthDTO;
import com.izylife.izykube.dto.kube.JobSummaryDTO;
import com.izylife.izykube.dto.kube.NamespaceDTO;
import com.izylife.izykube.dto.kube.NamespaceSummaryDTO;
import com.izylife.izykube.dto.kube.PodLogDTO;
import com.izylife.izykube.dto.kube.PodEventDTO;
import com.izylife.izykube.dto.kube.PodLogDetailsDTO;
import com.izylife.izykube.dto.kube.PodSummaryDTO;
import com.izylife.izykube.dto.kube.SecretSummaryDTO;
import com.izylife.izykube.dto.kube.ServiceSummaryDTO;
import com.izylife.izykube.dto.kube.StatefulSetSummaryDTO;
import com.izylife.izykube.repositories.ClusterRepository;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobStatus;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressClass;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.utils.Serialization;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KubernetesExplorerService {

    private static final String ALL_NAMESPACES = "all";
    private static final int DEFAULT_TAIL_LINES = 500;
    private static final int MAX_TAIL_LINES = 2000;
    private static final String INGRESS_NGINX_NAMESPACE = "ingress-nginx";
    private static final String INGRESS_NGINX_SERVICE = "ingress-nginx-controller";

    private final KubernetesClient kubernetesClient;
    private final ClusterRepository clusterRepository;

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

        List<IngressSummaryDTO> ingresses = (includeAll ? kubernetesClient.network().v1().ingresses().inAnyNamespace() : kubernetesClient.network().v1().ingresses().inNamespace(namespace))
                .list()
                .getItems()
                .stream()
                .map(this::mapIngress)
                .sorted(Comparator.comparing(IngressSummaryDTO::namespace).thenComparing(IngressSummaryDTO::name))
                .toList();

        List<ConfigMapSummaryDTO> configMaps = (includeAll ? kubernetesClient.configMaps().inAnyNamespace() : kubernetesClient.configMaps().inNamespace(namespace))
                .list()
                .getItems()
                .stream()
                .map(this::mapConfigMap)
                .sorted(Comparator.comparing(ConfigMapSummaryDTO::namespace).thenComparing(ConfigMapSummaryDTO::name))
                .toList();

        List<SecretSummaryDTO> secrets = (includeAll ? kubernetesClient.secrets().inAnyNamespace() : kubernetesClient.secrets().inNamespace(namespace))
                .list()
                .getItems()
                .stream()
                .map(this::mapSecret)
                .sorted(Comparator.comparing(SecretSummaryDTO::namespace).thenComparing(SecretSummaryDTO::name))
                .toList();

        List<JobSummaryDTO> jobs = (includeAll ? kubernetesClient.batch().v1().jobs().inAnyNamespace() : kubernetesClient.batch().v1().jobs().inNamespace(namespace))
                .list()
                .getItems()
                .stream()
                .map(this::mapJob)
                .sorted(Comparator.comparing(JobSummaryDTO::namespace).thenComparing(JobSummaryDTO::name))
                .toList();

        List<CronJobSummaryDTO> cronJobs = (includeAll ? kubernetesClient.batch().v1().cronjobs().inAnyNamespace() : kubernetesClient.batch().v1().cronjobs().inNamespace(namespace))
                .list()
                .getItems()
                .stream()
                .map(this::mapCronJob)
                .sorted(Comparator.comparing(CronJobSummaryDTO::namespace).thenComparing(CronJobSummaryDTO::name))
                .toList();

        List<DaemonSetSummaryDTO> daemonSets = (includeAll ? kubernetesClient.apps().daemonSets().inAnyNamespace() : kubernetesClient.apps().daemonSets().inNamespace(namespace))
                .list()
                .getItems()
                .stream()
                .map(this::mapDaemonSet)
                .sorted(Comparator.comparing(DaemonSetSummaryDTO::namespace).thenComparing(DaemonSetSummaryDTO::name))
                .toList();

        List<StatefulSetSummaryDTO> statefulSets = (includeAll ? kubernetesClient.apps().statefulSets().inAnyNamespace() : kubernetesClient.apps().statefulSets().inNamespace(namespace))
                .list()
                .getItems()
                .stream()
                .map(this::mapStatefulSet)
                .sorted(Comparator.comparing(StatefulSetSummaryDTO::namespace).thenComparing(StatefulSetSummaryDTO::name))
                .toList();

        return new NamespaceSummaryDTO(
                effectiveNamespace,
                pods,
                deployments,
                services,
                ingresses,
                configMaps,
                secrets,
                jobs,
                cronJobs,
                daemonSets,
                statefulSets
        );
    }

    public List<IngressClassSummaryDTO> listIngressClasses() {
        return kubernetesClient.network().v1().ingressClasses()
                .list()
                .getItems()
                .stream()
                .map(this::mapIngressClass)
                .sorted(Comparator.comparing(IngressClassSummaryDTO::name))
                .toList();
    }

    public IngressGatewayInfoDTO getIngressGatewayInfo() {
        io.fabric8.kubernetes.api.model.Service service = kubernetesClient.services()
                .inNamespace(INGRESS_NGINX_NAMESPACE)
                .withName(INGRESS_NGINX_SERVICE)
                .get();
        if (service == null || service.getSpec() == null) {
            return null;
        }

        String lbHost = Optional.ofNullable(service.getStatus())
                .map(status -> status.getLoadBalancer())
                .map(loadBalancer -> loadBalancer.getIngress())
                .orElse(List.of())
                .stream()
                .map(entry -> StringUtils.hasText(entry.getHostname()) ? entry.getHostname() : entry.getIp())
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);

        Integer httpNodePort = Optional.ofNullable(service.getSpec().getPorts())
                .orElse(List.of())
                .stream()
                .filter(port -> port != null && port.getPort() == 80)
                .map(port -> port.getNodePort())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        Integer httpsNodePort = Optional.ofNullable(service.getSpec().getPorts())
                .orElse(List.of())
                .stream()
                .filter(port -> port != null && port.getPort() == 443)
                .map(port -> port.getNodePort())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (StringUtils.hasText(lbHost)) {
            return new IngressGatewayInfoDTO(lbHost, 80, 443, true);
        }

        String nodeHost = kubernetesClient.nodes()
                .list()
                .getItems()
                .stream()
                .flatMap(node -> Optional.ofNullable(node.getStatus())
                        .map(status -> status.getAddresses())
                        .orElse(List.of())
                        .stream())
                .filter(address -> address != null && "InternalIP".equalsIgnoreCase(address.getType()))
                .map(address -> address.getAddress())
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);

        if (!StringUtils.hasText(nodeHost)) {
            return null;
        }

        return new IngressGatewayInfoDTO(nodeHost, httpNodePort, httpsNodePort, false);
    }

    public List<WorkloadHealthDTO> getWorkloadHealth(String namespace) {
        if (!StringUtils.hasText(namespace)) {
            return List.of();
        }
        List<WorkloadHealthDTO> results = new java.util.ArrayList<>();

        List<Deployment> deployments = kubernetesClient.apps().deployments().inNamespace(namespace).list().getItems();
        for (Deployment deployment : deployments) {
            results.add(buildWorkloadHealth("deployment", namespace, deployment.getMetadata().getName(),
                    Optional.ofNullable(deployment.getSpec()).map(spec -> spec.getSelector()).map(sel -> sel.getMatchLabels()).orElseGet(Collections::emptyMap)));
        }

        List<StatefulSet> statefulSets = kubernetesClient.apps().statefulSets().inNamespace(namespace).list().getItems();
        for (StatefulSet statefulSet : statefulSets) {
            results.add(buildWorkloadHealth("statefulset", namespace, statefulSet.getMetadata().getName(),
                    Optional.ofNullable(statefulSet.getSpec()).map(spec -> spec.getSelector()).map(sel -> sel.getMatchLabels()).orElseGet(Collections::emptyMap)));
        }

        List<DaemonSet> daemonSets = kubernetesClient.apps().daemonSets().inNamespace(namespace).list().getItems();
        for (DaemonSet daemonSet : daemonSets) {
            results.add(buildWorkloadHealth("daemonset", namespace, daemonSet.getMetadata().getName(),
                    Optional.ofNullable(daemonSet.getSpec()).map(spec -> spec.getSelector()).map(sel -> sel.getMatchLabels()).orElseGet(Collections::emptyMap)));
        }

        return results;
    }

    public String getResourceYaml(String kind, String namespace, String name) {
        if (!StringUtils.hasText(kind) || !StringUtils.hasText(namespace) || !StringUtils.hasText(name)) {
            return null;
        }
        Optional<? extends HasMetadata> resource = fetchResource(normalizeKind(kind), namespace, name);
        return resource.map(Serialization::asYaml).orElse(null);
    }

    public String applyResourceYaml(String kind, String namespace, String name, String yaml) {
        if (!StringUtils.hasText(kind) || !StringUtils.hasText(namespace) || !StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Kind, namespace, and name are required.");
        }
        if (!StringUtils.hasText(yaml)) {
            throw new IllegalArgumentException("YAML body is required.");
        }
        HasMetadata resource = Serialization.unmarshal(yaml);
        if (resource == null) {
            throw new IllegalArgumentException("Unable to parse YAML.");
        }
        String expectedKind = normalizeKind(kind);
        String resourceKind = normalizeKind(resource.getKind());
        if (!expectedKind.equals(resourceKind)) {
            throw new IllegalArgumentException("YAML kind does not match requested resource.");
        }
        if (resource.getMetadata() == null || !StringUtils.hasText(resource.getMetadata().getName())) {
            throw new IllegalArgumentException("YAML must include metadata.name.");
        }
        String resourceName = resource.getMetadata().getName();
        if (!name.equals(resourceName)) {
            throw new IllegalArgumentException("YAML metadata.name does not match requested resource.");
        }
        if (!StringUtils.hasText(resource.getMetadata().getNamespace())) {
            resource.getMetadata().setNamespace(namespace);
        } else if (!namespace.equals(resource.getMetadata().getNamespace())) {
            throw new IllegalArgumentException("YAML metadata.namespace does not match requested resource.");
        }

        HasMetadata updated = kubernetesClient.resource(resource)
                .inNamespace(namespace)
                .patch();
        if (updated == null) {
            throw new IllegalStateException("Resource update failed.");
        }
        return Serialization.asYaml(updated);
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

    public Pod getPod(String namespace, String podName) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(podName)) {
            return null;
        }
        return kubernetesClient.pods().inNamespace(namespace).withName(podName).get();
    }

    public PodLogDetailsDTO getPodLogsV1(String namespace, String podName, String container, int tailLines) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(podName)) {
            return null;
        }
        PodResource podResource = kubernetesClient.pods().inNamespace(namespace).withName(podName);
        Pod pod = podResource.get();
        if (pod == null) {
            return null;
        }

        String selectedContainer = StringUtils.hasText(container) ? container : selectDefaultContainer(pod);
        String logs = readPodLog(podResource, selectedContainer, sanitizeTail(tailLines));
        return new PodLogDetailsDTO(podName, namespace, selectedContainer, logs);
    }

    public List<PodEventDTO> getPodEvents(String namespace, String podName) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(podName)) {
            return List.of();
        }

        var options = new ListOptionsBuilder()
                .withFieldSelector("involvedObject.kind=Pod,involvedObject.name=" + podName)
                .build();

        EventList events = kubernetesClient.v1().events().inNamespace(namespace).list(options);
        if (events == null || events.getItems() == null) {
            return List.of();
        }

        return events.getItems().stream()
                .map(this::mapEvent)
                .toList();
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

        List<Pod> pods = findWorkloadPods(namespace, labelsToUse, deploymentName);

        int tail = sanitizeTail(tailLines);
        List<PodLogDTO> podLogs = pods.stream()
                .map(pod -> {
                    PodResource podResource = kubernetesClient.pods().inNamespace(namespace).withName(pod.getMetadata().getName());
                    String logContent = readPodLog(podResource, tail, false);
                    return new PodLogDTO(pod.getMetadata().getName(), namespace, logContent);
                })
                .toList();

        return new DeploymentLogsDTO(deploymentName, namespace, podLogs);
    }

    public DeploymentLogsDTO getWorkloadLogs(String kind, String namespace, String name, int tailLines, boolean previous) {
        if (!StringUtils.hasText(kind) || !StringUtils.hasText(namespace) || !StringUtils.hasText(name)) {
            return null;
        }
        String normalized = normalizeKind(kind);
        Map<String, String> selectorLabels = switch (normalized) {
            case "deployment" -> Optional.ofNullable(kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get())
                    .map(resource -> resource.getSpec())
                    .map(spec -> spec.getSelector())
                    .map(sel -> sel.getMatchLabels())
                    .orElseGet(Collections::emptyMap);
            case "statefulset" -> Optional.ofNullable(kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get())
                    .map(resource -> resource.getSpec())
                    .map(spec -> spec.getSelector())
                    .map(sel -> sel.getMatchLabels())
                    .orElseGet(Collections::emptyMap);
            case "daemonset" -> Optional.ofNullable(kubernetesClient.apps().daemonSets().inNamespace(namespace).withName(name).get())
                    .map(resource -> resource.getSpec())
                    .map(spec -> spec.getSelector())
                    .map(sel -> sel.getMatchLabels())
                    .orElseGet(Collections::emptyMap);
            default -> Collections.emptyMap();
        };

        List<Pod> pods = findWorkloadPods(namespace, selectorLabels, name);
        if (pods.isEmpty()) {
            return new DeploymentLogsDTO(name, namespace, List.of());
        }

        int tail = sanitizeTail(tailLines);
        List<PodLogDTO> podLogs = pods.stream()
                .map(pod -> {
                    PodResource podResource = kubernetesClient.pods().inNamespace(namespace).withName(pod.getMetadata().getName());
                    String logContent = readPodLog(podResource, tail, previous);
                    return new PodLogDTO(pod.getMetadata().getName(), namespace, logContent);
                })
                .toList();

        return new DeploymentLogsDTO(name, namespace, podLogs);
    }

    public List<PodSummaryDTO> getPodsByDeployment(String namespace, String deploymentName) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(deploymentName)) {
            return List.of();
        }

        Deployment deployment = kubernetesClient.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
        if (deployment == null) {
            return List.of();
        }

        Map<String, String> selectorLabels = Optional.ofNullable(deployment.getSpec())
                .map(spec -> spec.getSelector())
                .map(sel -> sel.getMatchLabels())
                .orElseGet(Collections::emptyMap);

        Map<String, String> labelsToUse = selectorLabels.isEmpty()
                ? Map.of("app", deploymentName)
                : selectorLabels;

        List<Pod> pods = findWorkloadPods(namespace, labelsToUse, deploymentName);

        return pods.stream()
                .map(this::mapPod)
                .sorted(Comparator.comparing(PodSummaryDTO::name))
                .toList();
    }

    public boolean isNamespaceDeployed(String namespace) {
        if (!StringUtils.hasText(namespace)) {
            return false;
        }
        return clusterRepository.findByNameSpaceIgnoreCase(namespace)
                .map(cluster -> cluster.getStatus() == ClusterStatusEnum.DEPLOYED)
                .orElse(false);
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

    private IngressSummaryDTO mapIngress(Ingress ingress) {
        String name = Optional.ofNullable(ingress.getMetadata()).map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "").orElse("");
        String namespace = Optional.ofNullable(ingress.getMetadata()).map(meta -> StringUtils.hasText(meta.getNamespace()) ? meta.getNamespace() : "").orElse("");
        String ingressClassName = Optional.ofNullable(ingress.getSpec())
                .map(spec -> StringUtils.hasText(spec.getIngressClassName()) ? spec.getIngressClassName() : "")
                .orElse("");
        String path = Optional.ofNullable(ingress.getSpec())
                .map(spec -> spec.getRules())
                .orElse(List.of())
                .stream()
                .map(IngressRule::getHttp)
                .filter(Objects::nonNull)
                .flatMap(http -> Optional.ofNullable(http.getPaths()).orElse(List.of()).stream())
                .map(HTTPIngressPath::getPath)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("/");
        String hosts = Optional.ofNullable(ingress.getSpec())
                .map(spec -> spec.getRules())
                .orElse(List.of())
                .stream()
                .map(rule -> StringUtils.hasText(rule.getHost()) ? rule.getHost() : "<all hosts>")
                .collect(Collectors.joining(", "));

        String services = Optional.ofNullable(ingress.getSpec())
                .map(spec -> spec.getRules())
                .orElse(List.of())
                .stream()
                .map(IngressRule::getHttp)
                .filter(Objects::nonNull)
                .flatMap(http -> Optional.ofNullable(http.getPaths()).orElse(List.of()).stream())
                .map(HTTPIngressPath::getBackend)
                .filter(Objects::nonNull)
                .map(backend -> Optional.ofNullable(backend.getService())
                        .map(serviceBackend -> {
                            String svcName = serviceBackend.getName();
                            Integer port = Optional.ofNullable(serviceBackend.getPort()).map(portSpec -> portSpec.getNumber()).orElse(null);
                            return port != null ? svcName + ":" + port : svcName;
                        })
                        .orElse("Custom backend"))
                .collect(Collectors.joining(", "));

        String tls = Optional.ofNullable(ingress.getSpec())
                .map(spec -> spec.getTls())
                .orElse(List.of())
                .stream()
                .map(entry -> {
                    String secretName = StringUtils.hasText(entry.getSecretName()) ? entry.getSecretName() : "no-secret";
                    String hostsEntry = Optional.ofNullable(entry.getHosts()).orElse(List.of())
                            .stream()
                            .filter(StringUtils::hasText)
                            .collect(Collectors.joining(", "));
                    return secretName + " (" + (StringUtils.hasText(hostsEntry) ? hostsEntry : "all hosts") + ")";
                })
                .collect(Collectors.joining("; "));

        String age = formatAge(ingress);

        return new IngressSummaryDTO(name, namespace, hosts, services, ingressClassName, path, tls, age);
    }

    private IngressClassSummaryDTO mapIngressClass(IngressClass ingressClass) {
        String name = Optional.ofNullable(ingressClass.getMetadata())
                .map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "")
                .orElse("");
        String controller = Optional.ofNullable(ingressClass.getSpec())
                .map(spec -> StringUtils.hasText(spec.getController()) ? spec.getController() : "")
                .orElse("");
        return new IngressClassSummaryDTO(name, controller);
    }

    private WorkloadHealthDTO buildWorkloadHealth(String kind, String namespace, String name, Map<String, String> selectorLabels) {
        List<Pod> pods = findWorkloadPods(namespace, selectorLabels, name);

        String reason = findUnhealthyReason(pods);
        boolean unhealthy = StringUtils.hasText(reason);
        return new WorkloadHealthDTO(kind, name, namespace, unhealthy, reason);
    }

    private String findUnhealthyReason(List<Pod> pods) {
        if (pods == null || pods.isEmpty()) {
            return "";
        }
        for (Pod pod : pods) {
            PodStatus status = pod.getStatus();
            List<io.fabric8.kubernetes.api.model.ContainerStatus> containerStatuses = Optional.ofNullable(status)
                    .map(PodStatus::getContainerStatuses)
                    .orElse(List.of());
            for (io.fabric8.kubernetes.api.model.ContainerStatus containerStatus : containerStatuses) {
                if (containerStatus == null) {
                    continue;
                }
                var state = containerStatus.getState();
                if (state != null && state.getWaiting() != null) {
                    String reason = state.getWaiting().getReason();
                    if (StringUtils.hasText(reason) && isFailureReason(reason)) {
                        return reason;
                    }
                }
                if (state != null && state.getTerminated() != null) {
                    Integer exitCode = state.getTerminated().getExitCode();
                    if (exitCode != null && exitCode != 0) {
                        return "Terminated (exit " + exitCode + ")";
                    }
                }
                if (!Boolean.TRUE.equals(containerStatus.getReady())) {
                    String waitingReason = state != null && state.getWaiting() != null ? state.getWaiting().getReason() : null;
                    if (StringUtils.hasText(waitingReason)) {
                        return waitingReason;
                    }
                }
            }

            List<PodCondition> conditions = Optional.ofNullable(status)
                    .map(PodStatus::getConditions)
                    .orElse(List.of());
            for (PodCondition condition : conditions) {
                if (condition != null && "Ready".equalsIgnoreCase(condition.getType()) && "False".equalsIgnoreCase(condition.getStatus())) {
                    if (StringUtils.hasText(condition.getReason())) {
                        return condition.getReason();
                    }
                    return "NotReady";
                }
            }
        }
        return "";
    }

    private boolean isFailureReason(String reason) {
        return switch (reason) {
            case "CrashLoopBackOff",
                 "ErrImagePull",
                 "ImagePullBackOff",
                 "CreateContainerConfigError",
                 "RunContainerError",
                 "ContainerCannotRun" -> true;
            default -> false;
        };
    }

    private List<Pod> findWorkloadPods(String namespace, Map<String, String> selectorLabels, String fallbackName) {
        Map<String, String> labelsToUse = selectorLabels == null ? Map.of() : selectorLabels;
        if (!labelsToUse.isEmpty()) {
            List<Pod> pods = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withLabels(labelsToUse)
                    .list()
                    .getItems();
            if (!pods.isEmpty()) {
                return pods;
            }
        }
        if (StringUtils.hasText(fallbackName)) {
            List<Pod> byApp = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withLabel("app", fallbackName)
                    .list()
                    .getItems();
            if (!byApp.isEmpty()) {
                return byApp;
            }
            List<Pod> byName = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withLabel("name", fallbackName)
                    .list()
                    .getItems();
            if (!byName.isEmpty()) {
                return byName;
            }
        }
        return List.of();
    }

    private Optional<? extends HasMetadata> fetchResource(String kind, String namespace, String name) {
        return switch (kind) {
            case "pod" -> Optional.ofNullable(kubernetesClient.pods().inNamespace(namespace).withName(name).get());
            case "deployment" -> Optional.ofNullable(kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get());
            case "statefulset" -> Optional.ofNullable(kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).get());
            case "daemonset" -> Optional.ofNullable(kubernetesClient.apps().daemonSets().inNamespace(namespace).withName(name).get());
            case "service" -> Optional.ofNullable(kubernetesClient.services().inNamespace(namespace).withName(name).get());
            case "configmap" -> Optional.ofNullable(kubernetesClient.configMaps().inNamespace(namespace).withName(name).get());
            case "secret" -> Optional.ofNullable(kubernetesClient.secrets().inNamespace(namespace).withName(name).get());
            case "job" -> Optional.ofNullable(kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(name).get());
            case "cronjob" -> Optional.ofNullable(kubernetesClient.batch().v1().cronjobs().inNamespace(namespace).withName(name).get());
            case "ingress" -> Optional.ofNullable(kubernetesClient.network().v1().ingresses().inNamespace(namespace).withName(name).get());
            default -> Optional.empty();
        };
    }

    private String normalizeKind(String kind) {
        if (!StringUtils.hasText(kind)) {
            return "";
        }
        String normalized = kind.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pods" -> "pod";
            case "deployments" -> "deployment";
            case "statefulsets" -> "statefulset";
            case "daemonsets" -> "daemonset";
            case "services" -> "service";
            case "configmaps" -> "configmap";
            case "secrets" -> "secret";
            case "jobs" -> "job";
            case "cronjobs" -> "cronjob";
            case "ingresses" -> "ingress";
            default -> normalized;
        };
    }

    private ConfigMapSummaryDTO mapConfigMap(ConfigMap configMap) {
        String name = Optional.ofNullable(configMap.getMetadata()).map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "").orElse("");
        String namespace = Optional.ofNullable(configMap.getMetadata()).map(meta -> StringUtils.hasText(meta.getNamespace()) ? meta.getNamespace() : "").orElse("");
        int dataEntries = Optional.ofNullable(configMap.getData()).map(Map::size).orElse(0)
                + Optional.ofNullable(configMap.getBinaryData()).map(Map::size).orElse(0);
        return new ConfigMapSummaryDTO(name, namespace, dataEntries, formatAge(configMap));
    }

    private SecretSummaryDTO mapSecret(Secret secret) {
        String name = Optional.ofNullable(secret.getMetadata()).map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "").orElse("");
        String namespace = Optional.ofNullable(secret.getMetadata()).map(meta -> StringUtils.hasText(meta.getNamespace()) ? meta.getNamespace() : "").orElse("");
        String type = Optional.ofNullable(secret.getType()).filter(StringUtils::hasText).orElse("Opaque");
        int dataEntries = Optional.ofNullable(secret.getData()).map(Map::size).orElse(0);
        return new SecretSummaryDTO(name, namespace, type, dataEntries, formatAge(secret));
    }

    private JobSummaryDTO mapJob(Job job) {
        String name = Optional.ofNullable(job.getMetadata()).map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "").orElse("");
        String namespace = Optional.ofNullable(job.getMetadata()).map(meta -> StringUtils.hasText(meta.getNamespace()) ? meta.getNamespace() : "").orElse("");
        var spec = Optional.ofNullable(job.getSpec());
        var status = Optional.ofNullable(job.getStatus());
        Integer completions = spec.map(s -> s.getCompletions()).orElse(null);
        Integer succeeded = status.map(s -> s.getSucceeded()).orElse(null);
        Integer failed = status.map(s -> s.getFailed()).orElse(null);
        Integer active = status.map(s -> s.getActive()).orElse(null);
        return new JobSummaryDTO(name, namespace, completions, succeeded, failed, active, formatAge(job));
    }

    private CronJobSummaryDTO mapCronJob(CronJob cronJob) {
        String name = Optional.ofNullable(cronJob.getMetadata()).map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "").orElse("");
        String namespace = Optional.ofNullable(cronJob.getMetadata()).map(meta -> StringUtils.hasText(meta.getNamespace()) ? meta.getNamespace() : "").orElse("");
        var spec = Optional.ofNullable(cronJob.getSpec());
        CronJobStatus status = cronJob.getStatus();
        String schedule = spec.map(s -> StringUtils.hasText(s.getSchedule()) ? s.getSchedule() : "-").orElse("-");
        boolean suspended = spec.map(s -> Optional.ofNullable(s.getSuspend()).orElse(false)).orElse(false);
        String lastScheduleTime = Optional.ofNullable(status)
                .map(CronJobStatus::getLastScheduleTime)
                .map(Object::toString)
                .orElse("-");
        int activeJobs = Optional.ofNullable(status)
                .map(s -> Optional.ofNullable(s.getActive()).orElse(List.of()).size())
                .orElse(0);
        return new CronJobSummaryDTO(name, namespace, schedule, suspended, lastScheduleTime, activeJobs, formatAge(cronJob));
    }

    private DaemonSetSummaryDTO mapDaemonSet(DaemonSet daemonSet) {
        String name = Optional.ofNullable(daemonSet.getMetadata()).map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "").orElse("");
        String namespace = Optional.ofNullable(daemonSet.getMetadata()).map(meta -> StringUtils.hasText(meta.getNamespace()) ? meta.getNamespace() : "").orElse("");
        var status = Optional.ofNullable(daemonSet.getStatus());
        Integer desired = status.map(s -> s.getDesiredNumberScheduled()).orElse(null);
        Integer current = status.map(s -> s.getCurrentNumberScheduled()).orElse(null);
        Integer ready = status.map(s -> s.getNumberReady()).orElse(null);
        Integer available = status.map(s -> s.getNumberAvailable()).orElse(null);
        Integer updated = status.map(s -> s.getUpdatedNumberScheduled()).orElse(null);
        return new DaemonSetSummaryDTO(name, namespace, desired, current, ready, available, updated, formatAge(daemonSet));
    }

    private StatefulSetSummaryDTO mapStatefulSet(StatefulSet statefulSet) {
        String name = Optional.ofNullable(statefulSet.getMetadata()).map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "").orElse("");
        String namespace = Optional.ofNullable(statefulSet.getMetadata()).map(meta -> StringUtils.hasText(meta.getNamespace()) ? meta.getNamespace() : "").orElse("");
        var status = Optional.ofNullable(statefulSet.getStatus());
        Integer readyReplicas = status.map(s -> s.getReadyReplicas()).orElse(null);
        Integer replicas = Optional.ofNullable(statefulSet.getSpec()).map(spec -> spec.getReplicas()).orElse(null);
        Integer updatedReplicas = status.map(s -> s.getUpdatedReplicas()).orElse(null);
        return new StatefulSetSummaryDTO(name, namespace, readyReplicas, replicas, updatedReplicas, formatAge(statefulSet));
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
        return readPodLog(podResource, null, tailLines, false);
    }

    private String readPodLog(PodResource podResource, int tailLines, boolean previous) {
        return readPodLog(podResource, null, tailLines, previous);
    }

    private String readPodLog(PodResource podResource, String container, int tailLines) {
        return readPodLog(podResource, container, tailLines, false);
    }

    private String readPodLog(PodResource podResource, String container, int tailLines, boolean previous) {
        try {
            var loggable = StringUtils.hasText(container)
                    ? podResource.inContainer(container)
                    : podResource;
            if (previous) {
                return Optional.ofNullable(loggable.terminated().tailingLines(tailLines).getLog()).orElse("");
            }
            return Optional.ofNullable(loggable.tailingLines(tailLines).getLog()).orElse("");
        } catch (Exception ex) {
            log.warn("Failed to read pod logs: {}", ex.getMessage());
            return "Unable to retrieve logs: " + ex.getMessage();
        }
    }

    private String selectDefaultContainer(Pod pod) {
        List<String> containerNames = Optional.ofNullable(pod.getSpec())
                .map(spec -> spec.getContainers())
                .orElse(List.of())
                .stream()
                .map(container -> container.getName())
                .filter(StringUtils::hasText)
                .toList();

        if (containerNames.isEmpty()) {
            return null;
        }

        for (String name : containerNames) {
            if (!isSidecarName(name)) {
                return name;
            }
        }
        return containerNames.get(0);
    }

    private boolean isSidecarName(String name) {
        String value = name.toLowerCase(Locale.ROOT);
        return value.equals("istio-proxy")
                || value.equals("linkerd-proxy")
                || value.equals("envoy")
                || value.endsWith("-proxy")
                || value.startsWith("istio");
    }

    private PodEventDTO mapEvent(Event event) {
        if (event == null) {
            return new PodEventDTO(null, null, null, null, null);
        }

        String timestamp = Optional.ofNullable(event.getLastTimestamp())
                .filter(StringUtils::hasText)
                .orElseGet(() -> Optional.ofNullable(event.getEventTime()).map(Object::toString).orElse(null));

        return new PodEventDTO(
                event.getType(),
                event.getReason(),
                event.getMessage(),
                timestamp,
                event.getCount()
        );
    }

    private int sanitizeTail(int tailLines) {
        if (tailLines <= 0) {
            return DEFAULT_TAIL_LINES;
        }
        return Math.min(tailLines, MAX_TAIL_LINES);
    }
}
