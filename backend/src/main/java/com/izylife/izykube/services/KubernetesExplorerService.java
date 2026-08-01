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

package com.izylife.izykube.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.izykube.collections.ClusterStatusEnum;
import com.izylife.izykube.dto.kube.ConfigMapSummaryDTO;
import com.izylife.izykube.dto.kube.CronJobSummaryDTO;
import com.izylife.izykube.dto.kube.DaemonSetSummaryDTO;
import com.izylife.izykube.dto.kube.DeploymentLogsDTO;
import com.izylife.izykube.dto.kube.DeploymentSummaryDTO;
import com.izylife.izykube.dto.kube.IstioGatewayInfoDTO;
import com.izylife.izykube.dto.kube.RouteSummaryDTO;
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
import com.izylife.izykube.model.Cluster;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobStatus;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Base64;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KubernetesExplorerService {

    private static final String ALL_NAMESPACES = "all";
    private static final int DEFAULT_TAIL_LINES = 500;
    private static final int MAX_TAIL_LINES = 2000;
    private static final String ISTIO_INGRESS_NAMESPACE = "istio-system";
    private static final String ISTIO_INGRESS_SERVICE = "istio-ingressgateway";
    private static final String CERT_MANAGER_NAMESPACE = "cert-manager";
    private static final String INTERNAL_CA_SECRET = "izykube-ca";
    private static final String ROUTE_STATUS_OUT_OF_SYNC = "OUT_OF_SYNC";

    private static final ResourceDefinitionContext VIRTUALSERVICE_CONTEXT = new ResourceDefinitionContext.Builder()
            .withGroup("networking.istio.io")
            .withVersion("v1beta1")
            .withKind("VirtualService")
            .withPlural("virtualservices")
            .build();

    private static final ResourceDefinitionContext GATEWAY_CONTEXT = new ResourceDefinitionContext.Builder()
            .withGroup("networking.istio.io")
            .withVersion("v1beta1")
            .withKind("Gateway")
            .withPlural("gateways")
            .build();

    private static final ResourceDefinitionContext VIRTUALSERVICE_CONTEXT_V1ALPHA3 = new ResourceDefinitionContext.Builder()
            .withGroup("networking.istio.io")
            .withVersion("v1alpha3")
            .withKind("VirtualService")
            .withPlural("virtualservices")
            .build();

    private static final ResourceDefinitionContext GATEWAY_CONTEXT_V1ALPHA3 = new ResourceDefinitionContext.Builder()
            .withGroup("networking.istio.io")
            .withVersion("v1alpha3")
            .withKind("Gateway")
            .withPlural("gateways")
            .build();

    private final KubernetesClient kubernetesClient;
    private final ClusterRepository clusterRepository;
    private final ObjectMapper objectMapper;

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

        List<RouteSummaryDTO> persistedRoutes = collectPersistedRoutes(includeAll ? null : namespace);
        boolean hasDeployedPersistedRoutes = persistedRoutes.stream()
                .anyMatch(route -> ClusterStatusEnum.DEPLOYED.getValue().equalsIgnoreCase(route.status()));
        boolean fetchLiveRoutes = includeAll
                || hasDeployedPersistedRoutes
                || (!includeAll && isNamespaceDeployed(namespace));
        List<RouteSummaryDTO> liveRoutes = fetchLiveRoutes
                ? fetchLiveRoutes(includeAll ? null : namespace, includeAll)
                : List.of();
        List<RouteSummaryDTO> mergedRoutes = reconcileRoutes(persistedRoutes, liveRoutes);

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
                mergedRoutes,
                configMaps,
                secrets,
                jobs,
                cronJobs,
                daemonSets,
                statefulSets
        );
    }

    private List<RouteSummaryDTO> fetchLiveRoutes(String namespace, boolean includeAll) {
        try {
            ResourceDefinitionContext virtualServiceContext = resolveVirtualServiceContext(includeAll ? null : namespace);
            return (includeAll
                    ? kubernetesClient.genericKubernetesResources(virtualServiceContext).inAnyNamespace()
                    : kubernetesClient.genericKubernetesResources(virtualServiceContext).inNamespace(namespace))
                    .list()
                    .getItems()
                    .stream()
                    .map(this::mapVirtualService)
                    .toList();
        } catch (KubernetesClientException ex) {
            if (ex.getCode() == 404) {
                log.info("Istio VirtualService CRD is not installed; omitting live routes from Kube Explorer");
                return List.of();
            }
            throw ex;
        }
    }

    private List<RouteSummaryDTO> reconcileRoutes(List<RouteSummaryDTO> persistedRoutes, List<RouteSummaryDTO> liveRoutes) {
        List<RouteSummaryDTO> canonicalPersisted = Optional.ofNullable(persistedRoutes).orElse(List.of()).stream()
                .filter(route -> route != null && StringUtils.hasText(route.namespace()) && StringUtils.hasText(route.name()))
                .toList();

        Map<String, List<RouteSummaryDTO>> liveByIdentity = indexLiveRoutes(liveRoutes);
        List<RouteSummaryDTO> reconciled = new ArrayList<>();

        for (RouteSummaryDTO persisted : canonicalPersisted) {
            boolean namespaceDeployed = ClusterStatusEnum.DEPLOYED.getValue().equalsIgnoreCase(persisted.status());
            if (!namespaceDeployed) {
                reconciled.add(persisted);
                continue;
            }

            RouteSummaryDTO liveMatch = consumeLiveMatch(liveByIdentity, persisted);
            if (liveMatch == null) {
                reconciled.add(withStatus(persisted, ROUTE_STATUS_OUT_OF_SYNC));
                continue;
            }
            reconciled.add(enrichFromLive(persisted, liveMatch));
        }

        Map<String, RouteSummaryDTO> existingByKey = reconciled.stream()
                .collect(Collectors.toMap(route -> routeKey(route.namespace(), route.name()), route -> route, (a, b) -> a, LinkedHashMap::new));
        for (RouteSummaryDTO live : Optional.ofNullable(liveRoutes).orElse(List.of())) {
            if (live == null || !StringUtils.hasText(live.namespace()) || !StringUtils.hasText(live.name())) {
                continue;
            }
            String key = routeKey(live.namespace(), live.name());
            if (existingByKey.containsKey(key)) {
                continue;
            }
            existingByKey.put(key, live);
        }

        return existingByKey.values().stream()
                .sorted(Comparator.comparing(RouteSummaryDTO::namespace).thenComparing(RouteSummaryDTO::name))
                .toList();
    }

    private Map<String, List<RouteSummaryDTO>> indexLiveRoutes(List<RouteSummaryDTO> liveRoutes) {
        Map<String, List<RouteSummaryDTO>> indexed = new LinkedHashMap<>();
        for (RouteSummaryDTO live : Optional.ofNullable(liveRoutes).orElse(List.of())) {
            if (live == null || !StringUtils.hasText(live.namespace()) || !StringUtils.hasText(live.name())) {
                continue;
            }
            for (String key : routeMatchKeys(live)) {
                indexed.computeIfAbsent(key, ignored -> new ArrayList<>()).add(live);
            }
        }
        return indexed;
    }

    private RouteSummaryDTO consumeLiveMatch(Map<String, List<RouteSummaryDTO>> liveByIdentity, RouteSummaryDTO persisted) {
        for (String key : routeMatchKeys(persisted)) {
            List<RouteSummaryDTO> candidates = liveByIdentity.get(key);
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }
            RouteSummaryDTO match = candidates.remove(0);
            removeRouteFromAllIdentityKeys(liveByIdentity, match);
            return match;
        }
        return null;
    }

    private void removeRouteFromAllIdentityKeys(Map<String, List<RouteSummaryDTO>> liveByIdentity, RouteSummaryDTO route) {
        for (String key : routeMatchKeys(route)) {
            List<RouteSummaryDTO> candidates = liveByIdentity.get(key);
            if (candidates == null) {
                continue;
            }
            candidates.removeIf(candidate -> Objects.equals(candidate.namespace(), route.namespace())
                    && Objects.equals(candidate.name(), route.name()));
            if (candidates.isEmpty()) {
                liveByIdentity.remove(key);
            }
        }
    }

    private List<String> routeMatchKeys(RouteSummaryDTO route) {
        String namespace = normalizeMatchValue(route.namespace());
        String name = normalizeMatchValue(route.name());
        String hosts = normalizeMatchValue(route.hosts());
        String serviceTarget = normalizeMatchValue(route.serviceTargets());

        List<String> keys = new ArrayList<>();
        keys.add(namespace + "|" + name);
        keys.add(namespace + "|" + name + "|" + hosts + "|" + serviceTarget);
        keys.add(namespace + "|" + hosts + "|" + serviceTarget);
        return keys;
    }

    private String normalizeMatchValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private RouteSummaryDTO enrichFromLive(RouteSummaryDTO persisted, RouteSummaryDTO live) {
        return new RouteSummaryDTO(
                persisted.name(),
                persisted.namespace(),
                StringUtils.hasText(live.hosts()) ? live.hosts() : persisted.hosts(),
                StringUtils.hasText(live.serviceTargets()) ? live.serviceTargets() : persisted.serviceTargets(),
                StringUtils.hasText(live.gatewayName()) ? live.gatewayName() : persisted.gatewayName(),
                StringUtils.hasText(live.path()) ? live.path() : persisted.path(),
                StringUtils.hasText(live.tls()) ? live.tls() : persisted.tls(),
                StringUtils.hasText(live.age()) ? live.age() : persisted.age(),
                StringUtils.hasText(live.status()) ? live.status() : persisted.status()
        );
    }

    private String routeKey(String namespace, String name) {
        return namespace.toLowerCase(Locale.ROOT) + "/" + name.toLowerCase(Locale.ROOT);
    }

    private List<RouteSummaryDTO> collectPersistedRoutes(String namespaceFilter) {
        List<Cluster> clusters = clusterRepository.findAll();
        if (clusters.isEmpty()) {
            return List.of();
        }
        List<RouteSummaryDTO> routes = new ArrayList<>();
        for (Cluster cluster : clusters) {
            if (cluster == null || !StringUtils.hasText(cluster.getDiagram())) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(cluster.getDiagram());
                JsonNode rawManifests = root.path("rawManifests");
                if (!rawManifests.isArray()) {
                    continue;
                }
                for (JsonNode entry : rawManifests) {
                    JsonNode manifestNode = entry.path("manifest");
                    if (!manifestNode.isObject()) {
                        continue;
                    }
                    String kind = textOrEmpty(entry, "kind");
                    if (!StringUtils.hasText(kind)) {
                        kind = textOrEmpty(manifestNode, "kind");
                    }
                    if (!"virtualservice".equalsIgnoreCase(kind)) {
                        continue;
                    }
                    RouteSummaryDTO route = mapVirtualServiceManifest(manifestNode, cluster.getNameSpace(), cluster.getStatus());
                    if (route == null) {
                        continue;
                    }
                    if (StringUtils.hasText(namespaceFilter)
                            && !namespaceFilter.equalsIgnoreCase(route.namespace())) {
                        continue;
                    }
                    routes.add(route);
                }
            } catch (Exception ex) {
                log.debug("Unable to parse persisted diagram for cluster {}: {}", cluster.getId(), ex.getMessage());
            }
        }

        Map<String, RouteSummaryDTO> unique = new LinkedHashMap<>();
        for (RouteSummaryDTO route : routes) {
            if (route == null || !StringUtils.hasText(route.namespace()) || !StringUtils.hasText(route.name())) {
                continue;
            }
            unique.putIfAbsent(routeKey(route.namespace(), route.name()), route);
        }
        return new ArrayList<>(unique.values());
    }

    private RouteSummaryDTO mapVirtualServiceManifest(JsonNode manifestNode,
                                                      String fallbackNamespace,
                                                      ClusterStatusEnum clusterStatus) {
        if (manifestNode == null || !manifestNode.isObject()) {
            return null;
        }
        JsonNode metadata = manifestNode.path("metadata");
        JsonNode spec = manifestNode.path("spec");

        String name = textOrEmpty(metadata, "name");
        String namespace = textOrEmpty(metadata, "namespace");
        if (!StringUtils.hasText(namespace)) {
            namespace = StringUtils.hasText(fallbackNamespace) ? fallbackNamespace : "default";
        }
        if (!StringUtils.hasText(name)) {
            return null;
        }

        List<String> hosts = readStringList(spec.path("hosts"));
        String hostsValue = normalizeHosts(hosts);

        String gatewayName = readStringList(spec.path("gateways")).stream().findFirst().orElse("");

        String path = "/";
        JsonNode http = spec.path("http");
        if (http.isArray() && !http.isEmpty()) {
            JsonNode firstHttp = http.get(0);
            JsonNode matches = firstHttp.path("match");
            if (matches.isArray() && !matches.isEmpty()) {
                String prefix = textOrEmpty(matches.get(0).path("uri"), "prefix");
                if (StringUtils.hasText(prefix)) {
                    path = prefix;
                }
            }
        }

        String serviceName = "";
        Integer servicePort = null;
        if (http.isArray() && !http.isEmpty()) {
            JsonNode routes = http.get(0).path("route");
            if (routes.isArray() && !routes.isEmpty()) {
                JsonNode destination = routes.get(0).path("destination");
                serviceName = textOrEmpty(destination, "host");
                String portValue = textOrEmpty(destination.path("port"), "number");
                if (StringUtils.hasText(portValue)) {
                    try {
                        servicePort = Integer.parseInt(portValue);
                    } catch (NumberFormatException ignored) {
                        servicePort = null;
                    }
                }
            }
        }
        String serviceTarget = StringUtils.hasText(serviceName)
                ? serviceName + (servicePort != null ? ":" + servicePort : "")
                : "";

        String age = "saved";
        String creationTimestamp = textOrEmpty(metadata, "creationTimestamp");
        if (StringUtils.hasText(creationTimestamp)) {
            age = formatAge(creationTimestamp);
        }

        String status = resolveRouteStatus(namespace, clusterStatus, false);
        return new RouteSummaryDTO(name, namespace, hostsValue, serviceTarget, gatewayName, path, "", age, status);
    }

    private String textOrEmpty(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        String text = value.asText("");
        return text == null ? "" : text.trim();
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item == null ? "" : item.asText("").trim();
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    public IstioGatewayInfoDTO getIstioGatewayInfo() {
        io.fabric8.kubernetes.api.model.Service service = kubernetesClient.services()
                .inNamespace(ISTIO_INGRESS_NAMESPACE)
                .withName(ISTIO_INGRESS_SERVICE)
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
            return new IstioGatewayInfoDTO(lbHost, 80, 443, true);
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

        return new IstioGatewayInfoDTO(nodeHost, httpNodePort, httpsNodePort, false);
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

    public void setDeploymentMesh(String namespace, String name, boolean enabled) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Namespace and deployment name are required.");
        }
        String resolvedNamespace = namespace.trim();
        Deployment deployment = kubernetesClient.apps().deployments().inNamespace(resolvedNamespace).withName(name).get();
        if (deployment == null) {
            throw new IllegalArgumentException("Deployment not found.");
        }
        String injectValue = enabled ? "true" : "false";
        String timestamp = Instant.now().toString();
        kubernetesClient.apps().deployments()
                .inNamespace(resolvedNamespace)
                .withName(name)
                .edit(d -> new DeploymentBuilder(d)
                        .editSpec().editTemplate().editMetadata()
                        .addToAnnotations("sidecar.istio.io/inject", injectValue)
                        .addToAnnotations("kubectl.kubernetes.io/restartedAt", timestamp)
                        .endMetadata().endTemplate().endSpec()
                        .build());
    }

    public byte[] getInternalCaCertificate() {
        Secret secret = kubernetesClient.secrets()
                .inNamespace(CERT_MANAGER_NAMESPACE)
                .withName(INTERNAL_CA_SECRET)
                .get();
        if (secret == null || CollectionUtils.isEmpty(secret.getData())) {
            return null;
        }
        String encoded = secret.getData().get("tls.crt");
        if (!StringUtils.hasText(encoded)) {
            encoded = secret.getData().get("ca.crt");
        }
        if (!StringUtils.hasText(encoded)) {
            return null;
        }
        return Base64.getDecoder().decode(encoded);
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
                    String mapping = String.valueOf(port.getPort());
                    IntOrString targetPort = port.getTargetPort();
                    Integer targetInt = targetPort != null ? targetPort.getIntVal() : null;
                    if (targetInt != null && targetInt > 0 && targetInt != port.getPort()) {
                        mapping = mapping + "->" + targetInt;
                    }
                    return mapping + (StringUtils.hasText(protocol) ? "/" + protocol : "");
                })
                .collect(Collectors.joining(", "));

        String age = formatAge(service);

        return new ServiceSummaryDTO(name, namespace, type, clusterIp, externalIp, ports, age);
    }

    private RouteSummaryDTO mapVirtualService(GenericKubernetesResource virtualService) {
        String name = Optional.ofNullable(virtualService.getMetadata())
                .map(meta -> StringUtils.hasText(meta.getName()) ? meta.getName() : "")
                .orElse("");
        String namespace = Optional.ofNullable(virtualService.getMetadata())
                .map(meta -> StringUtils.hasText(meta.getNamespace()) ? meta.getNamespace() : "")
                .orElse("");

        Map<String, Object> spec = getSpecMap(virtualService);
        List<String> hosts = getStringList(spec.get("hosts"));
        String hostsValue = normalizeHosts(hosts);
        String gatewayName = getStringList(spec.get("gateways")).stream().findFirst().orElse("");

        Map<String, Object> httpEntry = getFirstMap(spec.get("http"));
        String path = "/";
        if (!httpEntry.isEmpty()) {
            Map<String, Object> match = getFirstMap(httpEntry.get("match"));
            Map<String, Object> uri = getMap(match.get("uri"));
            path = Optional.ofNullable(uri.get("prefix"))
                    .map(Object::toString)
                    .filter(StringUtils::hasText)
                    .orElse(path);
        }

        Map<String, Object> routeEntry = getFirstMap(httpEntry.get("route"));
        Map<String, Object> destination = getMap(routeEntry.get("destination"));
        String serviceName = Optional.ofNullable(destination.get("host")).map(Object::toString).orElse("");
        Integer servicePort = Optional.ofNullable(getMap(destination.get("port")).get("number"))
                .filter(Objects::nonNull)
                .map(value -> Integer.parseInt(value.toString()))
                .orElse(null);
        String serviceTarget = StringUtils.hasText(serviceName)
                ? serviceName + (servicePort != null ? ":" + servicePort : "")
                : "";

        String tls = resolveGatewayTls(namespace, gatewayName);
        String age = formatAge(virtualService);
        String status = resolveRouteStatus(namespace, null, true);

        return new RouteSummaryDTO(name, namespace, hostsValue, serviceTarget, gatewayName, path, tls, age, status);
    }

    private RouteSummaryDTO withStatus(RouteSummaryDTO route, String status) {
        return new RouteSummaryDTO(
                route.name(),
                route.namespace(),
                route.hosts(),
                route.serviceTargets(),
                route.gatewayName(),
                route.path(),
                route.tls(),
                route.age(),
                status
        );
    }

    private String resolveRouteStatus(String namespace, ClusterStatusEnum clusterStatus, boolean liveRoute) {
        if (clusterStatus != null) {
            return clusterStatus.getValue();
        }
        if (StringUtils.hasText(namespace)) {
            ClusterStatusEnum resolved = clusterRepository.findByNameSpaceIgnoreCase(namespace)
                    .map(Cluster::getStatus)
                    .orElse(null);
            if (resolved != null) {
                return resolved.getValue();
            }
        }
        return liveRoute ? ClusterStatusEnum.DEPLOYED.getValue() : ClusterStatusEnum.INITIALIZED.getValue();
    }

    private String resolveGatewayTls(String namespace, String gatewayName) {
        if (!StringUtils.hasText(gatewayName)) {
            return "";
        }
        String resolvedNamespace = namespace;
        String resolvedName = gatewayName;
        if (gatewayName.contains("/")) {
            String[] parts = gatewayName.split("/", 2);
            resolvedNamespace = parts[0];
            resolvedName = parts[1];
        }
        GenericKubernetesResource gateway = kubernetesClient.genericKubernetesResources(resolveGatewayContext(resolvedNamespace))
                .inNamespace(resolvedNamespace)
                .withName(resolvedName)
                .get();
        if (gateway == null) {
            return "";
        }
        Map<String, Object> spec = getSpecMap(gateway);
        List<Map<String, Object>> servers = getMapList(spec.get("servers"));
        return servers.stream()
                .map(server -> getMap(server.get("tls")).get("credentialName"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private Map<String, Object> getSpecMap(GenericKubernetesResource resource) {
        Object spec = resource.getAdditionalProperties().get("spec");
        return getMap(spec);
    }

    private Map<String, Object> getMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, val) -> {
                if (key != null) {
                    result.put(key.toString(), val);
                }
            });
            return result;
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> getFirstMap(Object value) {
        List<Map<String, Object>> list = getMapList(value);
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    private List<Map<String, Object>> getMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?>) {
                result.add(getMap(entry));
            }
        }
        return result;
    }

    private List<String> getStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    private String normalizeHosts(List<String> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            return "<all hosts>";
        }
        if (hosts.stream().anyMatch(host -> host.equals("*"))) {
            return "<all hosts>";
        }
        return hosts.stream().filter(StringUtils::hasText).collect(Collectors.joining(", "));
    }

    private ResourceDefinitionContext resolveVirtualServiceContext(String namespace) {
        return resolveContext(VIRTUALSERVICE_CONTEXT, VIRTUALSERVICE_CONTEXT_V1ALPHA3, namespace);
    }

    private ResourceDefinitionContext resolveGatewayContext(String namespace) {
        return resolveContext(GATEWAY_CONTEXT, GATEWAY_CONTEXT_V1ALPHA3, namespace);
    }

    private ResourceDefinitionContext resolveContext(ResourceDefinitionContext primary,
                                                     ResourceDefinitionContext fallback,
                                                     String namespace) {
        try {
            if (namespace == null || namespace.isBlank()) {
                kubernetesClient.genericKubernetesResources(primary).inAnyNamespace().list();
            } else {
                kubernetesClient.genericKubernetesResources(primary).inNamespace(namespace).list();
            }
            return primary;
        } catch (Exception ex) {
            try {
                if (namespace == null || namespace.isBlank()) {
                    kubernetesClient.genericKubernetesResources(fallback).inAnyNamespace().list();
                } else {
                    kubernetesClient.genericKubernetesResources(fallback).inNamespace(namespace).list();
                }
                return fallback;
            } catch (Exception ignored) {
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new IllegalStateException("Istio CRDs not available for gateways/virtualservices.");
            }
        }
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
            case "virtualservice" -> Optional.ofNullable(kubernetesClient.genericKubernetesResources(VIRTUALSERVICE_CONTEXT).inNamespace(namespace).withName(name).get());
            case "gateway" -> Optional.ofNullable(kubernetesClient.genericKubernetesResources(GATEWAY_CONTEXT).inNamespace(namespace).withName(name).get());
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
            case "routes" -> "virtualservice";
            case "ingresses" -> "virtualservice";
            case "ingress" -> "virtualservice";
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
        return formatAge(timestamp);
    }

    private String formatAge(String timestamp) {
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
