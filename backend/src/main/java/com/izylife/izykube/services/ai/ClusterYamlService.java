package com.izylife.izykube.services.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.IngressDTO;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.PodDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class ClusterYamlService {

    private static final Map<String, String> ICON_MAP = Map.ofEntries(
            Map.entry("deployment", "assets/images/diagram/deployment.svg"),
            Map.entry("service", "assets/images/diagram/service.svg"),
            Map.entry("configmap", "assets/images/diagram/config-map.svg"),
            Map.entry("pod", "assets/images/diagram/pod.svg"),
            Map.entry("ingress", "assets/images/diagram/ingress.svg"),
            Map.entry("container", "assets/images/diagram/container.svg"),
            Map.entry("volume", "assets/images/diagram/volume.svg"),
            Map.entry("job", "assets/images/diagram/wrench.svg")
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Yaml yaml;

    public ClusterYamlService() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        this.yaml = new Yaml(options);
    }

    public ClusterDTO importCluster(String yamlContent, String overrideName) {
        if (yamlContent == null || yamlContent.isBlank()) {
            throw new ClusterYamlException("YAML content is empty.");
        }

        Iterable<Object> documents;
        try {
            documents = yaml.loadAll(normalizeYaml(yamlContent));
        } catch (YAMLException ex) {
            throw new ClusterYamlException("Invalid YAML content: " + ex.getMessage(), ex);
        }

        List<NodeDTO> nodes = new ArrayList<>();
        List<LinkDTO> links = new ArrayList<>();
        List<ManifestEntry> manifests = new ArrayList<>();

        Map<String, DeploymentInfo> deploymentInfoMap = new HashMap<>();
        Map<String, ServiceInfo> serviceInfoMap = new HashMap<>();
        List<IngressInfo> ingressInfoList = new ArrayList<>();

        String namespace = "default";

        try {
            for (Object document : documents) {
                if (!(document instanceof Map<?, ?> rawManifest)) {
                    continue;
                }

                Map<String, Object> manifest = deepCopy(rawManifest);
                String kind = getString(manifest, "kind", "").toLowerCase(Locale.ROOT);
                Map<String, Object> metadata = getMap(manifest, "metadata");
                if (metadata == null) {
                    metadata = new LinkedHashMap<>();
                    manifest.put("metadata", metadata);
                }

                String name = getString(metadata, "name", kind + "-" + randomSuffix());
                metadata.put("name", name);
                namespace = Optional.ofNullable(getString(metadata, "namespace", null)).orElse(namespace);

                ManifestEntry manifestEntry = new ManifestEntry(kind, name, manifest);
                manifests.add(manifestEntry);

                switch (kind) {
                    case "configmap" -> nodes.add(buildConfigMapNode(name, manifest));
                case "deployment" -> {
                    DeploymentArtifacts artifacts = buildDeploymentNodes(name, manifest);
                    nodes.add(artifacts.deploymentNode);
                    nodes.add(artifacts.podNode);
                    deploymentInfoMap.put(name, artifacts.info);
                    links.add(createLink(name, artifacts.podNode.getId()));
                }
                case "pod" -> {
                    PodDTO podNode = buildStandalonePodNode(name, manifest);
                    nodes.add(podNode);

                    DeploymentInfo info = new DeploymentInfo();
                    info.hasDeploymentNode = false;
                    info.podNodeId = podNode.getId();
                    info.labels = new LinkedHashMap<>();

                    Map<String, Object> podMetadata = getMap(manifest, "metadata");
                    if (podMetadata != null) {
                        info.labels.putAll(Optional.ofNullable(getMap(podMetadata, "labels"))
                                .orElseGet(LinkedHashMap::new));
                    }

                    Map<String, Object> spec = getMap(manifest, "spec");
                    info.referencedConfigMaps = extractConfigMapReferences(spec);
                    info.containerPorts = extractContainerPorts(spec);
                    info.podManifest = manifest;

                    deploymentInfoMap.put(podNode.getId(), info);
                }
                case "service" -> {
                    ServiceArtifacts artifacts = buildServiceNode(name, manifest);
                    nodes.add(artifacts.node);
                    serviceInfoMap.put(name, artifacts.info);
                }
                    case "ingress" -> {
                        IngressArtifacts artifacts = buildIngressNode(name, manifest);
                        nodes.add(artifacts.node);
                        ingressInfoList.add(artifacts.info);
                    }
                    case "virtualservice" -> {
                        IngressArtifacts artifacts = buildVirtualServiceNode(name, manifest);
                        nodes.add(artifacts.node);
                        ingressInfoList.add(artifacts.info);
                    }
                    default -> log.info("Skipping unsupported manifest kind: {}", kind);
                }
            }
        } catch (YAMLException ex) {
            throw new ClusterYamlException("Invalid YAML content: " + ex.getMessage(), ex);
        }

        // Link configmaps to deployments
        for (ManifestEntry entry : manifests) {
            if (!"configmap".equals(entry.kind)) {
                continue;
            }
            String configMapName = entry.name;
            deploymentInfoMap.forEach((deploymentName, info) -> {
                if (info.referencedConfigMaps.contains(configMapName)) {
                    String targetNodeId = info.hasDeploymentNode ? deploymentName : info.podNodeId;
                    links.add(createLink(configMapName, targetNodeId));
                }
            });
        }

        // Link deployments to services and pods to services
        serviceInfoMap.forEach((serviceName, info) -> {
            deploymentInfoMap.forEach((deploymentName, deploymentInfo) -> {
                if (matchesSelector(info.selector, deploymentInfo.labels)) {
                    if (deploymentInfo.hasDeploymentNode) {
                        links.add(createLink(deploymentName, serviceName));
                    }
                    links.add(createLink(deploymentInfo.podNodeId, serviceName));

                    ServiceDTO serviceNode = info.node;
                    if (serviceNode.getPort() == 0 && !deploymentInfo.containerPorts.isEmpty()) {
                        serviceNode.setPort(deploymentInfo.containerPorts.iterator().next());
                    }
                }
            });
        });

        // Link services to ingress/virtual services
        ingressInfoList.forEach(info -> {
            ServiceInfo serviceInfo = serviceInfoMap.get(info.targetServiceName);
            if (serviceInfo != null) {
                links.add(createLink(serviceInfo.node.getId(), info.nodeId));
                ServiceDTO serviceNode = serviceInfo.node;
                serviceNode.setExposeService(true);
                if (info.host != null) {
                    serviceNode.setFrontendUrl(buildFrontendUrl(info.host));
                }
            }
        });

        List<LinkDTO> mergedLinks = mergeDuplicates(links);

        ClusterDTO clusterDTO = new ClusterDTO();
        clusterDTO.setName(Optional.ofNullable(overrideName).filter(name -> !name.isBlank()).orElse("Imported Cluster"));
        clusterDTO.setNameSpace(namespace);
        clusterDTO.setNodes(nodes);
        clusterDTO.setLinks(mergedLinks);
        clusterDTO.setDiagram(buildDiagram(nodes, mergedLinks, manifests));
        return clusterDTO;
    }

    public String exportCluster(ClusterDTO cluster) {
        List<ManifestEntry> manifestEntries = buildUpdatedManifestEntries(cluster);
        return manifestEntries.stream()
                .map(entry -> yaml.dump(entry.getManifest()))
                .collect(Collectors.joining("---\n"));
    }

    public HelmChartArchive exportHelmChart(ClusterDTO cluster) {
        List<ManifestEntry> manifestEntries = buildUpdatedManifestEntries(cluster);
        if (manifestEntries.isEmpty()) {
            throw new IllegalStateException("Cluster does not contain any manifests to export");
        }

        String chartName = sanitizeChartName(Optional.ofNullable(cluster.getName()).orElse("cluster"));
        Map<String, Object> helmValues = new LinkedHashMap<>();
        List<HelmTemplateFile> templates = new ArrayList<>();

        int index = 1;
        for (ManifestEntry entry : manifestEntries) {
            Map<String, Object> manifestCopy = Optional.ofNullable(deepCopy(entry.getManifest()))
                    .orElseGet(LinkedHashMap::new);
            HelmValuesContext valuesContext = registerValuesContext(helmValues, entry.getKind(), entry.getName());
            applyHelmTransform(entry, manifestCopy, valuesContext);
            String templateFile = buildTemplateFileName(index++, entry, valuesContext);
            templates.add(new HelmTemplateFile("templates/" + templateFile, yaml.dump(manifestCopy)));
        }

        String chartYaml = buildChartYaml(chartName, cluster.getName());
        String valuesYaml = yaml.dump(helmValues);
        byte[] archiveContent = createChartArchive(chartName, chartYaml, valuesYaml, templates);

        return new HelmChartArchive(chartName + "-chart.zip", archiveContent);
    }

    private List<ManifestEntry> buildUpdatedManifestEntries(ClusterDTO cluster) {
        if (cluster == null) {
            throw new IllegalArgumentException("Cluster cannot be null");
        }

        List<ManifestEntry> manifestEntries = extractManifestEntries(cluster.getDiagram());
        Map<String, ManifestEntry> manifestsByName = manifestEntries.stream()
                .collect(Collectors.toMap(ManifestEntry::getName, entry -> entry, (a, b) -> a, LinkedHashMap::new));

        List<NodeDTO> nodes = cluster.getNodes() != null ? cluster.getNodes() : List.of();
        for (NodeDTO node : nodes) {
            if (node.getKind() == null) {
                continue;
            }
            switch (node.getKind().toLowerCase(Locale.ROOT)) {
                case "configmap" -> updateConfigMapManifest((ConfigMapDTO) node, manifestsByName);
                case "deployment" -> updateDeploymentManifest((DeploymentDTO) node, manifestsByName);
                case "service" -> updateServiceManifest((ServiceDTO) node, manifestsByName);
                case "ingress" -> updateIngressLikeManifest((IngressDTO) node, manifestsByName);
                case "pod" -> {
                    // Pods are generated from deployments; no standalone manifest required
                }
                default -> log.debug("Skipping export update for node kind: {}", node.getKind());
            }
        }

        return new ArrayList<>(manifestsByName.values());
    }

    private ConfigMapDTO buildConfigMapNode(String name, Map<String, Object> manifest) {
        String yamlContent = yaml.dump(manifest);
        return new ConfigMapDTO(name, name, yamlContent);
    }

    private DeploymentArtifacts buildDeploymentNodes(String name, Map<String, Object> manifest) {
        Map<String, Object> spec = getMap(manifest, "spec");
        int replicas = getInt(spec, "replicas", 1);
        String strategy = Optional.ofNullable(getMap(spec, "strategy"))
                .map(strategyMap -> getString(strategyMap, "type", "RollingUpdate"))
                .orElse("RollingUpdate");

        DeploymentDTO deploymentNode = new DeploymentDTO(name, name, replicas, strategy);

        Map<String, Object> template = Optional.ofNullable(getMap(spec, "template")).orElseGet(LinkedHashMap::new);
        Map<String, Object> podSpec = getMap(template, "spec");
        Map<String, Object> podMetadata = getMap(template, "metadata");

        PodDTO podNode = buildPodFromDeployment(name, podSpec);

        DeploymentInfo info = new DeploymentInfo();
        info.podNodeId = podNode.getId();
        info.labels = new LinkedHashMap<>();
        if (podMetadata != null) {
            info.labels.putAll(Optional.ofNullable(getMap(podMetadata, "labels")).orElseGet(LinkedHashMap::new));
        }
        info.labels.putAll(Optional.ofNullable(getMap(spec, "selector"))
                .map(selector -> getMap(selector, "matchLabels"))
                .orElseGet(LinkedHashMap::new));
        info.referencedConfigMaps = extractConfigMapReferences(podSpec);
        info.containerPorts = extractContainerPorts(podSpec);
        info.podManifest = manifest;

        return new DeploymentArtifacts(deploymentNode, podNode, info);
    }

    private ServiceArtifacts buildServiceNode(String name, Map<String, Object> manifest) {
        Map<String, Object> spec = getMap(manifest, "spec");
        String type = getString(spec, "type", "ClusterIP");
        int port = 0;
        Integer nodePort = null;

        List<Map<String, Object>> ports = getList(spec, "ports");
        if (ports != null && !ports.isEmpty()) {
            Map<String, Object> firstPort = ports.get(0);
            port = getInt(firstPort, "port", 80);
            nodePort = getInteger(firstPort, "nodePort");
        }

        ServiceDTO node = new ServiceDTO(name, name, type, port, nodePort, false, null);
        ServiceInfo info = new ServiceInfo(node, Optional.ofNullable(getMap(spec, "selector")).orElseGet(LinkedHashMap::new));
        info.manifest = manifest;
        return new ServiceArtifacts(node, info);
    }

    private IngressArtifacts buildIngressNode(String name, Map<String, Object> manifest) {
        Map<String, Object> spec = getMap(manifest, "spec");
        String host = null;
        String path = "/";
        String serviceName = null;
        int servicePort = 80;

        List<Map<String, Object>> rules = getList(spec, "rules");
        if (rules != null && !rules.isEmpty()) {
            Map<String, Object> rule = rules.get(0);
            host = getString(rule, "host", null);
            Map<String, Object> http = getMap(rule, "http");
            if (http != null) {
                List<Map<String, Object>> paths = getList(http, "paths");
                if (paths != null && !paths.isEmpty()) {
                    Map<String, Object> firstPath = paths.get(0);
                    path = getString(firstPath, "path", "/");
                    Map<String, Object> backend = getMap(firstPath, "backend");
                    if (backend != null) {
                        Map<String, Object> service = getMap(backend, "service");
                        if (service != null) {
                            serviceName = getString(service, "name", null);
                            Map<String, Object> portMap = getMap(service, "port");
                            if (portMap != null) {
                                servicePort = getInt(portMap, "number", servicePort);
                            }
                        }
                    }
                }
            }
        }

        IngressDTO node = new IngressDTO(name, name, host != null ? host : "example.com", path, serviceName != null ? serviceName : "", servicePort);
        IngressInfo info = new IngressInfo(node.getId(), serviceName, host);
        info.manifest = manifest;
        return new IngressArtifacts(node, info);
    }

    private IngressArtifacts buildVirtualServiceNode(String name, Map<String, Object> manifest) {
        Map<String, Object> spec = getMap(manifest, "spec");
        List<String> hosts = Optional.ofNullable(this.<String>getList(spec, "hosts"))
                .orElseGet(() -> new ArrayList<>());
        String host = hosts.isEmpty() ? "example.com" : hosts.get(0);
        String serviceName = null;
        int servicePort = 80;

        List<Map<String, Object>> http = getList(spec, "http");
        if (http != null && !http.isEmpty()) {
            Map<String, Object> firstHttp = http.get(0);
            List<Map<String, Object>> route = getList(firstHttp, "route");
            if (route != null && !route.isEmpty()) {
                Map<String, Object> destination = getMap(route.get(0), "destination");
                if (destination != null) {
                    String hostValue = getString(destination, "host", null);
                    if (hostValue != null) {
                        serviceName = hostValue.split("\\.")[0];
                    }
                    Map<String, Object> portMap = getMap(destination, "port");
                    if (portMap != null) {
                        servicePort = getInt(portMap, "number", servicePort);
                    }
                }
            }
        }

        IngressDTO node = new IngressDTO(name, name, host, "/", serviceName != null ? serviceName : "", servicePort);
        IngressInfo info = new IngressInfo(node.getId(), serviceName, host);
        info.manifest = manifest;
        return new IngressArtifacts(node, info);
    }

    private PodDTO buildPodFromDeployment(String deploymentName, Map<String, Object> podSpec) {
        String podId = deploymentName + "-pod";
        String podName = deploymentName + "-pod";
        return buildPod(podId, podName, podSpec);
    }

    private PodDTO buildStandalonePodNode(String podName, Map<String, Object> manifest) {
        Map<String, Object> podSpec = getMap(manifest, "spec");
        return buildPod(podName, podName, podSpec);
    }

    private PodDTO buildPod(String podId, String podName, Map<String, Object> podSpec) {
        String restartPolicy = getString(podSpec, "restartPolicy", "Always");
        String serviceAccount = getString(podSpec, "serviceAccountName", "default");
        Map<String, String> nodeSelector = Optional.ofNullable(getMap(podSpec, "nodeSelector"))
                .map(map -> map.entrySet().stream()
                        .collect(Collectors.toMap(entry -> entry.getKey(), entry -> Objects.toString(entry.getValue(), ""))))
                .orElse(null);

        Boolean hostNetwork = Optional.ofNullable(podSpec)
                .map(spec -> spec.get("hostNetwork"))
                .map(value -> value instanceof Boolean bool ? bool : null)
                .orElse(null);

        String dnsPolicy = getString(podSpec, "dnsPolicy", "ClusterFirst");
        String schedulerName = getString(podSpec, "schedulerName", "default-scheduler");
        Integer priority = getInteger(podSpec, "priority");
        String preemptionPolicy = getString(podSpec, "preemptionPolicy", "PreemptLowerPriority");

        return new PodDTO(podId, podName, restartPolicy, serviceAccount, nodeSelector, hostNetwork, dnsPolicy, schedulerName, priority, preemptionPolicy);
    }

    private Set<String> extractConfigMapReferences(Map<String, Object> podSpec) {
        Set<String> references = new HashSet<>();
        if (podSpec == null) {
            return references;
        }

        List<Map<String, Object>> volumes = getList(podSpec, "volumes");
        if (volumes != null) {
            for (Map<String, Object> volume : volumes) {
                Map<String, Object> configMap = getMap(volume, "configMap");
                if (configMap != null) {
                    String name = getString(configMap, "name", null);
                    if (name != null) {
                        references.add(name);
                    }
                }
            }
        }

        List<Map<String, Object>> containers = getList(podSpec, "containers");
        if (containers != null) {
            for (Map<String, Object> container : containers) {
                List<Map<String, Object>> envFrom = getList(container, "envFrom");
                if (envFrom != null) {
                    for (Map<String, Object> env : envFrom) {
                        Map<String, Object> configMapRef = getMap(env, "configMapRef");
                        if (configMapRef != null) {
                            String name = getString(configMapRef, "name", null);
                            if (name != null) {
                                references.add(name);
                            }
                        }
                    }
                }

                List<Map<String, Object>> env = getList(container, "env");
                if (env != null) {
                    for (Map<String, Object> envVar : env) {
                        Map<String, Object> valueFrom = getMap(envVar, "valueFrom");
                        if (valueFrom != null) {
                            Map<String, Object> configMapKeyRef = getMap(valueFrom, "configMapKeyRef");
                            if (configMapKeyRef != null) {
                                String name = getString(configMapKeyRef, "name", null);
                                if (name != null) {
                                    references.add(name);
                                }
                            }
                        }
                    }
                }
            }
        }

        return references;
    }

    private Set<Integer> extractContainerPorts(Map<String, Object> podSpec) {
        Set<Integer> ports = new HashSet<>();
        if (podSpec == null) {
            return ports;
        }
        List<Map<String, Object>> containers = getList(podSpec, "containers");
        if (containers == null) {
            return ports;
        }
        for (Map<String, Object> container : containers) {
            List<Map<String, Object>> containerPorts = getList(container, "ports");
            if (containerPorts == null) {
                continue;
            }
            for (Map<String, Object> port : containerPorts) {
                Integer value = getInteger(port, "containerPort");
                if (value != null) {
                    ports.add(value);
                }
            }
        }
        return ports;
    }

    private boolean matchesSelector(Map<String, Object> selector, Map<String, Object> labels) {
        if (selector == null || selector.isEmpty()) {
            return false;
        }
        if (labels == null || labels.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : selector.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!Objects.equals(labels.get(key), value)) {
                return false;
            }
        }
        return true;
    }

    private List<LinkDTO> mergeDuplicates(List<LinkDTO> links) {
        Map<String, LinkDTO> unique = new LinkedHashMap<>();
        for (LinkDTO link : links) {
            String key = link.getSource() + "->" + link.getTarget();
            unique.putIfAbsent(key, link);
        }
        return new ArrayList<>(unique.values());
    }

    private String buildDiagram(List<NodeDTO> nodes, List<LinkDTO> links, List<ManifestEntry> rawManifests) {
        List<Map<String, Object>> diagramNodes = new ArrayList<>();
        int spacingX = 200;
        int spacingY = 160;
        int startX = 160;
        int startY = 160;

        for (int index = 0; index < nodes.size(); index++) {
            NodeDTO node = nodes.get(index);
            int column = index % 4;
            int row = index / 4;

            Map<String, Object> diagramNode = new LinkedHashMap<>();
            diagramNode.put("id", node.getId());
            diagramNode.put("name", node.getName());
            diagramNode.put("type", node.getKind());
            diagramNode.put("icon", ICON_MAP.getOrDefault(node.getKind().toLowerCase(Locale.ROOT), ICON_MAP.get("container")));
            diagramNode.put("x", startX + column * spacingX);
            diagramNode.put("y", startY + row * spacingY);
            diagramNodes.add(diagramNode);
        }

        List<Map<String, Object>> diagramLinks = links.stream()
                .map(link -> {
                    Map<String, Object> linkMap = new LinkedHashMap<>();
                    linkMap.put("id", link.getSource() + "->" + link.getTarget());
                    linkMap.put("from", link.getSource());
                    linkMap.put("to", link.getTarget());
                    return linkMap;
                })
                .collect(Collectors.toList());

        Map<String, Object> diagram = new LinkedHashMap<>();
        diagram.put("nodes", diagramNodes);
        diagram.put("links", diagramLinks);
        diagram.put("rawManifests", rawManifests.stream()
                .map(entry -> Map.of(
                        "kind", entry.kind,
                        "name", entry.name,
                        "manifest", entry.manifest
                )).collect(Collectors.toList()));

        try {
            return objectMapper.writeValueAsString(diagram);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build diagram representation", e);
        }
    }

    private List<ManifestEntry> extractManifestEntries(String diagramJson) {
        if (diagramJson == null || diagramJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            Map<String, Object> diagramMap = objectMapper.readValue(
                    diagramJson,
                    TypeFactory.defaultInstance().constructMapType(LinkedHashMap.class, String.class, Object.class)
            );
            List<Map<String, Object>> raw = getList(diagramMap, "rawManifests");
            if (raw == null) {
                return new ArrayList<>();
            }
            List<ManifestEntry> entries = new ArrayList<>();
            for (Map<String, Object> entry : raw) {
                String kind = Objects.toString(entry.get("kind"), "").toLowerCase(Locale.ROOT);
                String name = Objects.toString(entry.get("name"), "");
                Map<String, Object> manifest = deepCopy(entry.get("manifest"));
                entries.add(new ManifestEntry(kind, name, manifest));
            }
            return entries;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse diagram JSON: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void updateConfigMapManifest(ConfigMapDTO node, Map<String, ManifestEntry> manifests) {
        ManifestEntry entry = manifests.get(node.getId());
        if (entry == null) {
            Map<String, Object> manifest = yaml.load(node.getYaml());
            entry = new ManifestEntry("configmap", node.getId(), manifest);
            manifests.put(node.getId(), entry);
            return;
        }
        Map<String, Object> manifest = yaml.load(node.getYaml());
        entry.manifest = manifest;
    }

    private void updateDeploymentManifest(DeploymentDTO node, Map<String, ManifestEntry> manifests) {
        ManifestEntry entry = manifests.get(node.getId());
        if (entry == null) {
            entry = new ManifestEntry("deployment", node.getId(), createBaseDeploymentManifest(node));
            manifests.put(node.getId(), entry);
        }
        Map<String, Object> manifest = entry.manifest;
        manifest.put("apiVersion", manifest.getOrDefault("apiVersion", "apps/v1"));
        manifest.put("kind", "Deployment");
        Map<String, Object> metadata = Optional.ofNullable(getMap(manifest, "metadata")).orElseGet(LinkedHashMap::new);
        metadata.put("name", node.getName());
        manifest.put("metadata", metadata);

        Map<String, Object> spec = Optional.ofNullable(getMap(manifest, "spec")).orElseGet(LinkedHashMap::new);
        spec.put("replicas", node.getReplicas());
        Map<String, Object> strategy = Optional.ofNullable(getMap(spec, "strategy")).orElseGet(LinkedHashMap::new);
        strategy.put("type", node.getStrategyType());
        spec.put("strategy", strategy);
        manifest.put("spec", spec);
    }

    private void updateServiceManifest(ServiceDTO node, Map<String, ManifestEntry> manifests) {
        ManifestEntry entry = manifests.get(node.getId());
        if (entry == null) {
            entry = new ManifestEntry("service", node.getId(), createBaseServiceManifest(node));
            manifests.put(node.getId(), entry);
        }

        Map<String, Object> manifest = entry.manifest;
        manifest.put("apiVersion", manifest.getOrDefault("apiVersion", "v1"));
        manifest.put("kind", "Service");
        Map<String, Object> metadata = Optional.ofNullable(getMap(manifest, "metadata")).orElseGet(LinkedHashMap::new);
        metadata.put("name", node.getName());
        manifest.put("metadata", metadata);

        Map<String, Object> spec = Optional.ofNullable(getMap(manifest, "spec")).orElseGet(LinkedHashMap::new);
        spec.put("type", node.getType());
        List<Map<String, Object>> ports = Optional.ofNullable(this.<Map<String, Object>>getList(spec, "ports"))
                .orElseGet(() -> new ArrayList<>());
        Map<String, Object> firstPort = ports.isEmpty() ? new LinkedHashMap<>() : ports.get(0);
        firstPort.put("port", node.getPort());
        if (node.getNodePort() != null && "NodePort".equalsIgnoreCase(node.getType())) {
            firstPort.put("nodePort", node.getNodePort());
        } else {
            firstPort.remove("nodePort");
        }
        if (ports.isEmpty()) {
            ports.add(firstPort);
        }
        spec.put("ports", ports);
        manifest.put("spec", spec);
    }

    private void updateIngressLikeManifest(IngressDTO node, Map<String, ManifestEntry> manifests) {
        ManifestEntry entry = manifests.get(node.getId());
        if (entry == null) {
            entry = new ManifestEntry("ingress", node.getId(), createBaseIngressManifest(node));
            manifests.put(node.getId(), entry);
        }
        Map<String, Object> manifest = entry.manifest;
        manifest.put("apiVersion", manifest.getOrDefault("apiVersion", entry.kind.equals("virtualservice") ? "networking.istio.io/v1alpha3" : "networking.k8s.io/v1"));
        manifest.put("kind", entry.kind.equals("virtualservice") ? "VirtualService" : "Ingress");

        Map<String, Object> metadata = Optional.ofNullable(getMap(manifest, "metadata")).orElseGet(LinkedHashMap::new);
        metadata.put("name", node.getName());
        manifest.put("metadata", metadata);

        if (entry.kind.equals("virtualservice")) {
            updateVirtualServiceManifest(manifest, node);
        } else {
            updateIngressManifest(manifest, node);
        }
    }

    private void updateIngressManifest(Map<String, Object> manifest, IngressDTO node) {
        Map<String, Object> spec = Optional.ofNullable(getMap(manifest, "spec")).orElseGet(LinkedHashMap::new);
        List<Map<String, Object>> rules = Optional.ofNullable(this.<Map<String, Object>>getList(spec, "rules"))
                .orElseGet(() -> new ArrayList<>());
        Map<String, Object> rule = rules.isEmpty() ? new LinkedHashMap<>() : rules.get(0);
        rule.put("host", node.getHost());
        Map<String, Object> http = Optional.ofNullable(getMap(rule, "http")).orElseGet(LinkedHashMap::new);
        List<Map<String, Object>> paths = Optional.ofNullable(this.<Map<String, Object>>getList(http, "paths"))
                .orElseGet(() -> new ArrayList<>());
        Map<String, Object> path = paths.isEmpty() ? new LinkedHashMap<>() : paths.get(0);
        path.put("path", node.getPath());
        path.put("pathType", path.getOrDefault("pathType", "Prefix"));
        Map<String, Object> backend = Optional.ofNullable(getMap(path, "backend")).orElseGet(LinkedHashMap::new);
        Map<String, Object> service = Optional.ofNullable(getMap(backend, "service")).orElseGet(LinkedHashMap::new);
        service.put("name", node.getServiceName());
        Map<String, Object> port = Optional.ofNullable(getMap(service, "port")).orElseGet(LinkedHashMap::new);
        port.put("number", node.getServicePort());
        service.put("port", port);
        backend.put("service", service);
        path.put("backend", backend);
        if (paths.isEmpty()) {
            paths.add(path);
        }
        http.put("paths", paths);
        rule.put("http", http);
        if (rules.isEmpty()) {
            rules.add(rule);
        }
        spec.put("rules", rules);
        manifest.put("spec", spec);
    }

    private void updateVirtualServiceManifest(Map<String, Object> manifest, IngressDTO node) {
        Map<String, Object> spec = Optional.ofNullable(getMap(manifest, "spec")).orElseGet(LinkedHashMap::new);
        List<String> hosts = Optional.ofNullable(this.<String>getList(spec, "hosts"))
                .orElseGet(() -> new ArrayList<>());
        if (hosts.isEmpty()) {
            hosts.add(node.getHost());
        } else {
            hosts.set(0, node.getHost());
        }
        spec.put("hosts", hosts);

        List<Map<String, Object>> http = Optional.ofNullable(this.<Map<String, Object>>getList(spec, "http"))
                .orElseGet(() -> new ArrayList<>());
        Map<String, Object> httpEntry = http.isEmpty() ? new LinkedHashMap<>() : http.get(0);
        List<Map<String, Object>> routes = Optional.ofNullable(this.<Map<String, Object>>getList(httpEntry, "route"))
                .orElseGet(() -> new ArrayList<>());
        Map<String, Object> route = routes.isEmpty() ? new LinkedHashMap<>() : routes.get(0);
        Map<String, Object> destination = Optional.ofNullable(getMap(route, "destination")).orElseGet(LinkedHashMap::new);
        destination.put("host", node.getServiceName());
        Map<String, Object> port = Optional.ofNullable(getMap(destination, "port")).orElseGet(LinkedHashMap::new);
        port.put("number", node.getServicePort());
        destination.put("port", port);
        route.put("destination", destination);
        if (routes.isEmpty()) {
            routes.add(route);
        }
        httpEntry.put("route", routes);
        if (http.isEmpty()) {
            http.add(httpEntry);
        }
        spec.put("http", http);
        manifest.put("spec", spec);
    }

    private Map<String, Object> createBaseDeploymentManifest(DeploymentDTO node) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", "apps/v1");
        manifest.put("kind", "Deployment");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", node.getName());
        manifest.put("metadata", metadata);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("replicas", node.getReplicas());
        Map<String, Object> selector = new LinkedHashMap<>();
        selector.put("matchLabels", Map.of("app", node.getName()));
        spec.put("selector", selector);
        Map<String, Object> template = new LinkedHashMap<>();
        Map<String, Object> tempMeta = new LinkedHashMap<>();
        tempMeta.put("labels", Map.of("app", node.getName()));
        template.put("metadata", tempMeta);
        Map<String, Object> tempSpec = new LinkedHashMap<>();
        tempSpec.put("containers", List.of(Map.of("name", node.getName(), "image", "", "ports", List.of(Map.of("containerPort", 8080)))));
        template.put("spec", tempSpec);
        spec.put("template", template);
        manifest.put("spec", spec);
        return manifest;
    }

    private Map<String, Object> createBaseServiceManifest(ServiceDTO node) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", "v1");
        manifest.put("kind", "Service");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", node.getName());
        manifest.put("metadata", metadata);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("type", node.getType());
        Map<String, Object> ports = new LinkedHashMap<>();
        ports.put("port", node.getPort());
        spec.put("ports", List.of(ports));
        manifest.put("spec", spec);
        return manifest;
    }

    private Map<String, Object> createBaseIngressManifest(IngressDTO node) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", "networking.k8s.io/v1");
        manifest.put("kind", "Ingress");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", node.getName());
        manifest.put("metadata", metadata);
        return manifest;
    }

    private String buildChartYaml(String chartName, String clusterName) {
        String description = Optional.ofNullable(clusterName)
                .filter(name -> !name.isBlank())
                .map(name -> "Generated Helm chart for " + name)
                .orElse("Generated Helm chart");
        return """
                apiVersion: v2
                name: %s
                description: %s
                type: application
                version: 0.1.0
                appVersion: "1.0.0"
                """.formatted(chartName, description);
    }

    private String sanitizeChartName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "cluster";
        }
        String sanitized = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        sanitized = sanitized.replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (sanitized.isBlank()) {
            return "cluster";
        }
        return sanitized;
    }

    private byte[] createChartArchive(String chartName, String chartYaml, String valuesYaml, List<HelmTemplateFile> templates) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {

            writeZipEntry(zipOutputStream, chartName + "/Chart.yaml", chartYaml);
            writeZipEntry(zipOutputStream, chartName + "/values.yaml", valuesYaml);
            for (HelmTemplateFile template : templates) {
                writeZipEntry(zipOutputStream, chartName + "/" + template.path(), template.content());
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create Helm chart archive", e);
        }
    }

    private void writeZipEntry(ZipOutputStream zipOutputStream, String path, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(path));
        if (content != null && !content.isEmpty()) {
            zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        }
        zipOutputStream.closeEntry();
    }

    private String buildTemplateFileName(int index, ManifestEntry entry, HelmValuesContext context) {
        String kind = Optional.ofNullable(entry.getKind()).orElse("resource");
        Object resolvedName = context.values.get("name");
        String name = resolvedName != null ? resolvedName.toString() : entry.getName();
        String safeKind = kind.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        if (safeKind.isBlank()) {
            safeKind = "resource";
        }
        String safeName = name == null ? safeKind : name.replaceAll("[^a-zA-Z0-9.-]", "-").toLowerCase(Locale.ROOT);
        safeName = safeName.replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (safeName.isBlank()) {
            safeName = safeKind;
        }
        return "%02d-%s-%s.yaml".formatted(index, safeKind, safeName);
    }

    private void applyHelmTransform(ManifestEntry entry, Map<String, Object> manifest, HelmValuesContext context) {
        String kind = Optional.ofNullable(entry.getKind()).orElse("");
        switch (kind) {
            case "deployment" -> applyDeploymentHelmValues(manifest, entry.getName(), context);
            case "service" -> applyServiceHelmValues(manifest, entry.getName(), context);
            case "ingress" -> applyIngressHelmValues(manifest, entry.getName(), context);
            case "virtualservice" -> applyVirtualServiceHelmValues(manifest, entry.getName(), context);
            default -> templateResourceName(manifest, entry.getName(), context);
        }
    }

    private void applyDeploymentHelmValues(Map<String, Object> manifest, String fallbackName, HelmValuesContext context) {
        templateResourceName(manifest, fallbackName, context);
        Map<String, Object> spec = ensureChildMap(manifest, "spec");
        int replicas = getInt(spec, "replicas", 1);
        context.values.put("replicas", replicas);
        spec.put("replicas", context.valueRef("replicas"));

        Map<String, Object> template = ensureChildMap(spec, "template");
        Map<String, Object> templateSpec = ensureChildMap(template, "spec");
        List<Map<String, Object>> containers = this.<Map<String, Object>>getList(templateSpec, "containers");
        if (containers != null && !containers.isEmpty()) {
            Map<String, Object> containersValues = new LinkedHashMap<>();
            context.values.put("containers", containersValues);
            for (Map<String, Object> container : containers) {
                if (container == null) {
                    continue;
                }
                String containerName = getString(container, "name", "container");
                String containerKey = ensureUniqueHelmKey(containerName, containersValues);
                Map<String, Object> containerEntry = new LinkedHashMap<>();
                containerEntry.put("name", containerName);
                containerEntry.put("image", getString(container, "image", ""));
                containersValues.put(containerKey, containerEntry);
                container.put("name", context.valueRef("containers." + containerKey + ".name"));
                container.put("image", context.valueRef("containers." + containerKey + ".image"));
            }
        }
    }

    private void applyServiceHelmValues(Map<String, Object> manifest, String fallbackName, HelmValuesContext context) {
        templateResourceName(manifest, fallbackName, context);
        Map<String, Object> spec = ensureChildMap(manifest, "spec");
        String type = getString(spec, "type", "ClusterIP");
        context.values.put("type", type);
        spec.put("type", context.valueRef("type"));

        List<Map<String, Object>> ports = this.<Map<String, Object>>getList(spec, "ports");
        if (ports != null && !ports.isEmpty()) {
            Map<String, Object> firstPort = ports.get(0);
            Integer port = getInteger(firstPort, "port");
            if (port != null) {
                context.values.put("port", port);
                firstPort.put("port", context.valueRef("port"));
            }
            Integer targetPort = getInteger(firstPort, "targetPort");
            if (targetPort != null) {
                context.values.put("targetPort", targetPort);
                firstPort.put("targetPort", context.valueRef("targetPort"));
            }
            Integer nodePort = getInteger(firstPort, "nodePort");
            if (nodePort != null) {
                context.values.put("nodePort", nodePort);
                firstPort.put("nodePort", context.valueRef("nodePort"));
            }
        }
    }

    private void applyIngressHelmValues(Map<String, Object> manifest, String fallbackName, HelmValuesContext context) {
        templateResourceName(manifest, fallbackName, context);
        Map<String, Object> spec = ensureChildMap(manifest, "spec");
        List<Map<String, Object>> rules = this.<Map<String, Object>>getList(spec, "rules");
        if (rules == null || rules.isEmpty()) {
            return;
        }
        Map<String, Object> rule = rules.get(0);
        String host = getString(rule, "host", null);
        if (host != null && !host.isBlank()) {
            context.values.put("host", host);
            rule.put("host", context.valueRef("host"));
        }
        Map<String, Object> http = ensureChildMap(rule, "http");
        List<Map<String, Object>> paths = this.<Map<String, Object>>getList(http, "paths");
        if (paths == null || paths.isEmpty()) {
            return;
        }
        Map<String, Object> path = paths.get(0);
        Map<String, Object> backend = ensureChildMap(path, "backend");
        Map<String, Object> service = ensureChildMap(backend, "service");
        String serviceName = getString(service, "name", null);
        if (serviceName != null && !serviceName.isBlank()) {
            context.values.put("serviceName", serviceName);
            service.put("name", context.valueRef("serviceName"));
        }
        Map<String, Object> port = ensureChildMap(service, "port");
        Integer servicePort = getInteger(port, "number");
        if (servicePort != null) {
            context.values.put("servicePort", servicePort);
            port.put("number", context.valueRef("servicePort"));
        }
    }

    private void applyVirtualServiceHelmValues(Map<String, Object> manifest, String fallbackName, HelmValuesContext context) {
        templateResourceName(manifest, fallbackName, context);
        Map<String, Object> spec = ensureChildMap(manifest, "spec");
        List<String> hosts = this.<String>getList(spec, "hosts");
        if (hosts != null && !hosts.isEmpty()) {
            String host = hosts.get(0);
            if (host != null && !host.isBlank()) {
                context.values.put("host", host);
                hosts.set(0, context.valueRef("host"));
            }
        }
        List<Map<String, Object>> http = this.<Map<String, Object>>getList(spec, "http");
        if (http == null || http.isEmpty()) {
            return;
        }
        Map<String, Object> httpEntry = http.get(0);
        List<Map<String, Object>> routes = this.<Map<String, Object>>getList(httpEntry, "route");
        if (routes == null || routes.isEmpty()) {
            return;
        }
        Map<String, Object> route = routes.get(0);
        Map<String, Object> destination = ensureChildMap(route, "destination");
        String hostName = getString(destination, "host", null);
        if (hostName != null && !hostName.isBlank()) {
            context.values.put("serviceName", hostName);
            destination.put("host", context.valueRef("serviceName"));
        }
        Map<String, Object> port = ensureChildMap(destination, "port");
        Integer number = getInteger(port, "number");
        if (number != null) {
            context.values.put("servicePort", number);
            port.put("number", context.valueRef("servicePort"));
        }
    }

    private void templateResourceName(Map<String, Object> manifest, String fallbackName, HelmValuesContext context) {
        Map<String, Object> metadata = ensureChildMap(manifest, "metadata");
        String resolvedFallback = (fallbackName == null || fallbackName.isBlank()) ? context.key : fallbackName;
        String resourceName = getString(metadata, "name", resolvedFallback);
        context.values.putIfAbsent("name", resourceName);
        metadata.put("name", context.valueRef("name"));
    }

    private Map<String, Object> ensureChildMap(Map<String, Object> parent, String key) {
        if (parent == null) {
            throw new IllegalArgumentException("Parent map cannot be null for key: " + key);
        }
        return Optional.ofNullable(getMap(parent, key)).orElseGet(() -> {
            Map<String, Object> child = new LinkedHashMap<>();
            parent.put(key, child);
            return child;
        });
    }

    private HelmValuesContext registerValuesContext(Map<String, Object> valuesRoot, String kind, String resourceName) {
        String section = resolveValuesSection(kind);
        Map<String, Object> sectionValues = ensureSection(valuesRoot, section);
        String key = ensureUniqueHelmKey(resourceName, sectionValues);
        Map<String, Object> entryValues = new LinkedHashMap<>();
        sectionValues.put(key, entryValues);
        return new HelmValuesContext(section, key, entryValues);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureSection(Map<String, Object> valuesRoot, String section) {
        return (Map<String, Object>) valuesRoot.computeIfAbsent(section, key -> new LinkedHashMap<>());
    }

    private String resolveValuesSection(String kind) {
        if (kind == null) {
            return "resources";
        }
        return switch (kind) {
            case "deployment" -> "deployments";
            case "service" -> "services";
            case "ingress", "virtualservice" -> "ingresses";
            case "configmap" -> "configmaps";
            default -> "resources";
        };
    }

    private String ensureUniqueHelmKey(String resourceName, Map<String, Object> existingEntries) {
        String base = sanitizeHelmKey(resourceName);
        String candidate = base;
        int suffix = 1;
        while (existingEntries.containsKey(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private String sanitizeHelmKey(String resourceName) {
        String value = resourceName == null ? "" : resourceName.toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9]+", "_");
        value = value.replaceAll("_+", "_");
        value = value.replaceAll("^_+", "");
        value = value.replaceAll("_+$", "");
        if (value.isBlank()) {
            value = "resource";
        }
        if (Character.isDigit(value.charAt(0))) {
            value = "r_" + value;
        }
        return value;
    }

    private LinkDTO createLink(String from, String to) {
        LinkDTO link = new LinkDTO();
        link.setSource(from);
        link.setTarget(to);
        return link;
    }

    private Map<String, Object> deepCopy(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getList(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        if (value instanceof List<?> list) {
            return (List<T>) list;
        }
        return null;
    }

    private String getString(Map<String, Object> source, String key, String defaultValue) {
        if (source == null) {
            return defaultValue;
        }
        Object value = source.get(key);
        if (value == null) {
            return defaultValue;
        }
        String asString = value.toString();
        return asString.isBlank() ? defaultValue : asString;
    }

    private int getInt(Map<String, Object> source, String key, int defaultValue) {
        Integer integer = getInteger(source, key);
        return integer != null ? integer : defaultValue;
    }

    private Integer getInteger(Map<String, Object> source, String key) {
        if (source == null || !source.containsKey(key)) {
            return null;
        }
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private boolean getBoolean(Map<String, Object> source, String key, boolean defaultValue) {
        if (source == null || !source.containsKey(key)) {
            return defaultValue;
        }
        Object value = source.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string.trim());
        }
        return defaultValue;
    }

    private String buildFrontendUrl(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return host;
        }
        return "https://" + host;
    }

    private String normalizeYaml(String yamlContent) {
        String content = yamlContent.replace("\r\n", "\n").replace('\r', '\n');
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }

        String[] lines = content.split("\n", -1);
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingIndent(line);
            if (indent < minIndent) {
                minIndent = indent;
                if (minIndent == 0) {
                    break;
                }
            }
        }

        if (minIndent != Integer.MAX_VALUE && minIndent > 0) {
            StringBuilder builder = new StringBuilder(content.length());
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                builder.append(removeIndent(line, minIndent));
                if (i < lines.length - 1) {
                    builder.append('\n');
                }
            }
            content = builder.toString();
        }

        return content;
    }

    private int countLeadingIndent(String line) {
        int index = 0;
        while (index < line.length()) {
            char c = line.charAt(index);
            if (c == ' ' || c == '\t') {
                index++;
                continue;
            }
            break;
        }
        return index;
    }

    private String removeIndent(String line, int indent) {
        if (line.isEmpty()) {
            return line;
        }
        int index = 0;
        int remaining = indent;
        while (index < line.length() && remaining > 0) {
            char c = line.charAt(index);
            if (c == ' ' || c == '\t') {
                index++;
                remaining--;
            } else {
                break;
            }
        }
        return line.substring(index);
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    private record HelmTemplateFile(String path, String content) {
    }

    private static class HelmValuesContext {
        private final String section;
        private final String key;
        private final Map<String, Object> values;

        HelmValuesContext(String section, String key, Map<String, Object> values) {
            this.section = section;
            this.key = key;
            this.values = values;
        }

        String valueRef(String attributePath) {
            if (attributePath == null || attributePath.isBlank()) {
                return "{{ .Values.%s.%s }}".formatted(section, key);
            }
            return "{{ .Values.%s.%s.%s }}".formatted(section, key, attributePath);
        }
    }

    private static class ManifestEntry {
        private final String kind;
        private final String name;
        private Map<String, Object> manifest;

        ManifestEntry(String kind, String name, Map<String, Object> manifest) {
            this.kind = kind;
            this.name = name;
            this.manifest = manifest;
        }

        public String getName() {
            return name;
        }

        public String getKind() {
            return kind;
        }

        public Map<String, Object> getManifest() {
            return manifest;
        }
    }

    private static class DeploymentInfo {
        String podNodeId;
        Map<String, Object> labels;
        Set<String> referencedConfigMaps = new HashSet<>();
        Set<Integer> containerPorts = new HashSet<>();
        Map<String, Object> podManifest;
        boolean hasDeploymentNode = true;
    }

    private static class DeploymentArtifacts {
        final DeploymentDTO deploymentNode;
        final PodDTO podNode;
        final DeploymentInfo info;

        DeploymentArtifacts(DeploymentDTO deploymentNode, PodDTO podNode, DeploymentInfo info) {
            this.deploymentNode = deploymentNode;
            this.podNode = podNode;
            this.info = info;
        }
    }

    private static class ServiceInfo {
        final ServiceDTO node;
        final Map<String, Object> selector;
        Map<String, Object> manifest;

        ServiceInfo(ServiceDTO node, Map<String, Object> selector) {
            this.node = node;
            this.selector = selector;
        }
    }

    private static class ServiceArtifacts {
        final ServiceDTO node;
        final ServiceInfo info;

        ServiceArtifacts(ServiceDTO node, ServiceInfo info) {
            this.node = node;
            this.info = info;
        }
    }

    private static class IngressInfo {
        final String nodeId;
        final String targetServiceName;
        final String host;
        Map<String, Object> manifest;

        IngressInfo(String nodeId, String targetServiceName, String host) {
            this.nodeId = nodeId;
            this.targetServiceName = targetServiceName;
            this.host = host;
        }
    }

    private static class IngressArtifacts {
        final IngressDTO node;
        final IngressInfo info;

        IngressArtifacts(IngressDTO node, IngressInfo info) {
            this.node = node;
            this.info = info;
        }
    }
}
