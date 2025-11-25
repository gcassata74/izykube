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
import com.izylife.izykube.dto.cluster.SecretDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import com.izylife.izykube.dto.cluster.VirtualServiceDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
            Map.entry("secret", "assets/images/diagram/secret.svg"),
            Map.entry("ingress", "assets/images/diagram/ingress.svg"),
            Map.entry("container", "assets/images/diagram/container.svg"),
            Map.entry("volume", "assets/images/diagram/volume.svg"),
            Map.entry("job", "assets/images/diagram/wrench.svg"),
            Map.entry("istio", "assets/images/diagram/istio.svg")
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
        List<VirtualServiceInfo> virtualServiceInfoList = new ArrayList<>();

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
                    case "configmap", "secret" -> nodes.add(buildConfigMapNode(name, manifest));
                case "deployment" -> {
                    DeploymentArtifacts artifacts = buildDeploymentNodes(name, manifest);
                    nodes.add(artifacts.deploymentNode);
                    deploymentInfoMap.put(artifacts.deploymentNode.getId(), artifacts.info);
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
                        VirtualServiceArtifacts artifacts = buildVirtualServiceNode(name, manifest);
                        nodes.add(artifacts.node);
                        virtualServiceInfoList.add(artifacts.info);
                    }
                    default -> log.info("Skipping unsupported manifest kind: {}", kind);
                }
            }
        } catch (YAMLException ex) {
            throw new ClusterYamlException("Invalid YAML content: " + ex.getMessage(), ex);
        }

        linkConfigAndSecretResources(links, manifests, deploymentInfoMap);
        linkServicesToWorkloads(links, serviceInfoMap, deploymentInfoMap);
        linkIngressTargets(links, ingressInfoList, serviceInfoMap);
        linkVirtualServiceTargets(links, virtualServiceInfoList, serviceInfoMap);

        List<LinkDTO> mergedLinks = mergeDuplicates(links);
        List<Map<String, Object>> manifestSnapshots = manifests.stream()
                .map(entry -> Map.<String, Object>of(
                        "kind", entry.kind,
                        "name", entry.name,
                        "manifest", entry.manifest
                ))
                .collect(Collectors.toList());

        ClusterDTO clusterDTO = new ClusterDTO();
        clusterDTO.setName(Optional.ofNullable(overrideName).filter(name -> !name.isBlank()).orElse("Imported Cluster"));
        clusterDTO.setNameSpace(namespace);
        clusterDTO.setNodes(nodes);
        clusterDTO.setLinks(mergedLinks);
        clusterDTO.setDiagram(buildDiagramSnapshot(nodes, mergedLinks, manifestSnapshots));
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
        Map<String, NodeDTO> nodesById = nodes.stream()
                .filter(node -> node.getId() != null)
                .collect(Collectors.toMap(NodeDTO::getId, node -> node, (a, b) -> a, LinkedHashMap::new));
        Map<String, List<NodeDTO>> targetsBySource = buildTargetsBySource(nodesById, cluster.getLinks());
        for (NodeDTO node : nodes) {
            if (node.getKind() == null) {
                continue;
            }
            switch (node.getKind().toLowerCase(Locale.ROOT)) {
                case "configmap" -> updateConfigMapManifest((ConfigMapDTO) node, manifestsByName);
                case "secret" -> updateSecretManifest((SecretDTO) node, manifestsByName);
                case "deployment" -> updateDeploymentManifest((DeploymentDTO) node, manifestsByName);
                case "service" -> updateServiceManifest((ServiceDTO) node, manifestsByName);
                case "ingress" -> {
                    resolveIngressTargetsFromLinks((IngressDTO) node, targetsBySource);
                    updateIngressLikeManifest((IngressDTO) node, manifestsByName);
                }
                case "istio", "virtualservice" -> {
                    resolveVirtualServiceTargetsFromLinks((VirtualServiceDTO) node, targetsBySource);
                    updateVirtualServiceManifestEntry((VirtualServiceDTO) node, manifestsByName);
                }
                default -> log.debug("Skipping export update for node kind: {}", node.getKind());
            }
        }

        return new ArrayList<>(manifestsByName.values());
    }

    private ConfigMapDTO buildConfigMapNode(String name, Map<String, Object> manifest) {
        String kind = getString(manifest, "kind", "");
        boolean isSecret = "secret".equalsIgnoreCase(kind);
        Map<String, Object> dataSection = Optional.ofNullable(getMap(manifest, "data"))
                .orElseGet(() -> getMap(manifest, "stringData"));

        Map<String, String> values = new LinkedHashMap<>();
        if (dataSection != null) {
            dataSection.forEach((key, value) -> {
                String normalized = normalizeScalar(value);
                values.put(String.valueOf(key), isSecret ? decodeSecretValue(normalized) : normalized);
            });
        }

        String yamlContent = values.isEmpty() ? "" : yaml.dump(values);
        String nodeId = generateNodeId(isSecret ? "secret" : "configmap", name);
        return isSecret
                ? new SecretDTO(nodeId, name, yamlContent)
                : new ConfigMapDTO(nodeId, name, yamlContent);
    }

    private DeploymentArtifacts buildDeploymentNodes(String name, Map<String, Object> manifest) {
        Map<String, Object> spec = getMap(manifest, "spec");
        int replicas = getInt(spec, "replicas", 1);
        String strategy = Optional.ofNullable(getMap(spec, "strategy"))
                .map(strategyMap -> getString(strategyMap, "type", "RollingUpdate"))
                .orElse("RollingUpdate");

        DeploymentDTO deploymentNode = new DeploymentDTO(generateNodeId("deployment", name), name, replicas, strategy, "", 80);

        Map<String, Object> template = Optional.ofNullable(getMap(spec, "template")).orElseGet(LinkedHashMap::new);
        Map<String, Object> podSpec = getMap(template, "spec");
        Map<String, Object> podMetadata = getMap(template, "metadata");

        DeploymentInfo info = new DeploymentInfo();
        info.labels.putAll(LabelMatcher.normalize(getMap(podMetadata, "labels")));
        Map<String, Object> selector = getMap(spec, "selector");
        Map<String, Object> matchLabels = selector != null ? getMap(selector, "matchLabels") : null;
        info.labels.putAll(LabelMatcher.normalize(matchLabels));
        info.referencedConfigResources.addAll(extractConfigAndSecretReferences(podSpec));
        info.containerPorts.addAll(extractContainerPorts(podSpec));

        info.containerPorts.stream().findFirst().ifPresent(port -> deploymentNode.setContainerPort(port));

        return new DeploymentArtifacts(deploymentNode, info);
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

        ServiceDTO node = new ServiceDTO(generateNodeId("service", name), name, type, port, nodePort, false, null);
        ServiceInfo info = new ServiceInfo(node, LabelMatcher.normalize(getMap(spec, "selector")));
        info.manifest = manifest;
        return new ServiceArtifacts(node, info);
    }

    private IngressArtifacts buildIngressNode(String name, Map<String, Object> manifest) {
        Map<String, Object> spec = getMap(manifest, "spec");
        Map<String, Object> metadata = getMap(manifest, "metadata");
        String host = null;
        String path = "/";
        String serviceName = null;
        int servicePort = 80;
        String tlsSecret = null;

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

        List<Map<String, Object>> tls = getList(spec, "tls");
        if (tls != null && !tls.isEmpty()) {
            tlsSecret = getString(tls.get(0), "secretName", null);
        }

        Map<String, String> annotations = extractAnnotations(metadata);

        IngressDTO node = new IngressDTO(
                generateNodeId("ingress", name),
                name,
                host != null ? host : "example.com",
                path,
                serviceName != null ? serviceName : "",
                servicePort,
                tlsSecret,
                annotations
        );
        IngressInfo info = new IngressInfo(node.getId(), serviceName, host);
        info.manifest = manifest;
        return new IngressArtifacts(node, info);
    }

    private VirtualServiceArtifacts buildVirtualServiceNode(String name, Map<String, Object> manifest) {
        Map<String, Object> spec = getMap(manifest, "spec");
        List<String> hosts = Optional.ofNullable(this.<String>getList(spec, "hosts")).orElseGet(ArrayList::new);
        String host = hosts.isEmpty() ? "example.com" : hosts.get(0);
        List<String> targetServices = new ArrayList<>();
        int fallbackPort = 80;

        List<Map<String, Object>> http = getList(spec, "http");
        if (http != null) {
            for (Map<String, Object> httpEntry : http) {
                List<Map<String, Object>> routes = getList(httpEntry, "route");
                if (routes == null) {
                    continue;
                }
                for (Map<String, Object> route : routes) {
                    Map<String, Object> destination = getMap(route, "destination");
                    if (destination == null) {
                        continue;
                    }
                    String hostValue = getString(destination, "host", null);
                    if (hostValue != null) {
                        targetServices.add(extractServiceShortName(hostValue));
                    }
                    Map<String, Object> portMap = getMap(destination, "port");
                    if (portMap != null) {
                        fallbackPort = getInt(portMap, "number", fallbackPort);
                    }
                }
            }
        }

        String primaryService = targetServices.isEmpty() ? "" : targetServices.get(0);
        VirtualServiceDTO node = new VirtualServiceDTO(
                generateNodeId("istio", name),
                name,
                host,
                "/",
                primaryService,
                fallbackPort
        );
        VirtualServiceInfo info = new VirtualServiceInfo(node.getId(), targetServices, host);
        info.manifest = manifest;
        return new VirtualServiceArtifacts(node, info);
    }

    private Set<String> extractConfigAndSecretReferences(Map<String, Object> podSpec) {
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
                Map<String, Object> secret = getMap(volume, "secret");
                if (secret != null) {
                    String secretName = getString(secret, "secretName", null);
                    if (secretName != null) {
                        references.add(secretName);
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
                        Map<String, Object> secretRef = getMap(env, "secretRef");
                        if (secretRef != null) {
                            String name = getString(secretRef, "name", null);
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
                            Map<String, Object> secretKeyRef = getMap(valueFrom, "secretKeyRef");
                            if (secretKeyRef != null) {
                                String name = getString(secretKeyRef, "name", null);
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

    private void linkConfigAndSecretResources(List<LinkDTO> links,
                                              List<ManifestEntry> manifests,
                                              Map<String, DeploymentInfo> deploymentInfoMap) {
        if (manifests.isEmpty() || deploymentInfoMap.isEmpty()) {
            return;
        }
        manifests.stream()
                .filter(entry -> "configmap".equals(entry.kind) || "secret".equals(entry.kind))
                .forEach(entry -> {
                    String sourceNodeId = generateNodeId(entry.kind, entry.name);
                    deploymentInfoMap.forEach((workloadId, info) -> {
                        if (!info.referencedConfigResources.contains(entry.name)) {
                            return;
                        }
                        links.add(createLink(sourceNodeId, workloadId));
                    });
                });
    }

    private void linkServicesToWorkloads(List<LinkDTO> links,
                                         Map<String, ServiceInfo> serviceInfoMap,
                                         Map<String, DeploymentInfo> deploymentInfoMap) {
        if (serviceInfoMap.isEmpty() || deploymentInfoMap.isEmpty()) {
            return;
        }
        serviceInfoMap.values().forEach(serviceInfo -> deploymentInfoMap.forEach((workloadId, workloadInfo) -> {
            if (!LabelMatcher.matchesNormalized(serviceInfo.selector, workloadInfo.labels)) {
                return;
            }
            String serviceNodeId = serviceInfo.node.getId();
            links.add(createLink(serviceNodeId, workloadId));
            propagateServicePort(serviceInfo.node, workloadInfo);
        }));
    }

    private void propagateServicePort(ServiceDTO serviceNode, DeploymentInfo workloadInfo) {
        if (serviceNode.getPort() == 0 && !workloadInfo.containerPorts.isEmpty()) {
            serviceNode.setPort(workloadInfo.containerPorts.iterator().next());
        }
    }

    private void linkIngressTargets(List<LinkDTO> links,
                                    List<IngressInfo> ingressInfoList,
                                    Map<String, ServiceInfo> serviceInfoMap) {
        if (ingressInfoList.isEmpty() || serviceInfoMap.isEmpty()) {
            return;
        }
        ingressInfoList.forEach(info -> {
            ServiceInfo serviceInfo = serviceInfoMap.get(info.targetServiceName);
            if (serviceInfo == null) {
                return;
            }
            links.add(createLink(info.nodeId, serviceInfo.node.getId()));
            ServiceDTO serviceNode = serviceInfo.node;
            serviceNode.setExposeService(true);
            if (info.host != null) {
                serviceNode.setFrontendUrl(buildFrontendUrl(info.host));
            }
        });
    }

    private void linkVirtualServiceTargets(List<LinkDTO> links,
                                           List<VirtualServiceInfo> virtualServiceInfoList,
                                           Map<String, ServiceInfo> serviceInfoMap) {
        if (virtualServiceInfoList.isEmpty() || serviceInfoMap.isEmpty()) {
            return;
        }
        virtualServiceInfoList.forEach(info -> {
            for (String serviceName : info.targetServiceNames) {
                ServiceInfo serviceInfo = serviceInfoMap.get(serviceName);
                if (serviceInfo == null) {
                    continue;
                }
                links.add(createLink(info.nodeId, serviceInfo.node.getId()));
            }
        });
    }

    private List<LinkDTO> mergeDuplicates(List<LinkDTO> links) {
        Map<String, LinkDTO> unique = new LinkedHashMap<>();
        for (LinkDTO link : links) {
            String key = link.getSource() + "->" + link.getTarget();
            unique.putIfAbsent(key, link);
        }
        return new ArrayList<>(unique.values());
    }

    public String buildDiagramSnapshot(List<NodeDTO> nodes,
                                       List<LinkDTO> links,
                                       List<Map<String, Object>> rawManifests) {
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
        diagram.put("rawManifests", Optional.ofNullable(rawManifests).orElse(Collections.emptyList()));

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
        String resourceName = resolveResourceName(node);
        Map<String, Object> manifest = buildKeyValueManifest(node, resourceName, false);
        manifests.put(node.getId(), new ManifestEntry("configmap", resourceName, manifest));
    }

    private void updateSecretManifest(SecretDTO node, Map<String, ManifestEntry> manifests) {
        String resourceName = resolveResourceName(node);
        Map<String, Object> manifest = buildKeyValueManifest(node, resourceName, true);
        manifests.put(node.getId(), new ManifestEntry("secret", resourceName, manifest));
    }

    private Map<String, Object> buildKeyValueManifest(ConfigMapDTO node, String resourceName, boolean secret) {
        Map<String, Object> manifest = Optional.ofNullable(loadManifestFromYaml(node.getYaml()))
                .map(this::deepCopy)
                .orElseGet(() -> createBaseManifest(resourceName, secret ? "Secret" : "ConfigMap"));

        manifest.put("apiVersion", manifest.getOrDefault("apiVersion", "v1"));
        manifest.put("kind", secret ? "Secret" : "ConfigMap");

        Map<String, Object> metadata = Optional.ofNullable(getMap(manifest, "metadata")).orElseGet(LinkedHashMap::new);
        metadata.putIfAbsent("name", resourceName);
        metadata.putIfAbsent("namespace", "default");
        manifest.put("metadata", metadata);

        Map<String, String> values = extractPlainKeyValueData(node.getYaml(), secret);
        Map<String, Object> dataSection = new LinkedHashMap<>();
        values.forEach((key, value) -> dataSection.put(key, secret ? encodeSecretValue(value) : value));
        manifest.put("data", dataSection);
        if (secret) {
            manifest.remove("stringData");
        }
        return manifest;
    }

    private String resolveResourceName(ConfigMapDTO node) {
        return Optional.ofNullable(node.getName()).filter(name -> !name.isBlank()).orElse(node.getId());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadManifestFromYaml(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return null;
        }
        Object parsed = yaml.load(yamlContent);
        if (parsed instanceof Map<?, ?> map && map.containsKey("kind")) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private Map<String, String> extractPlainKeyValueData(String yamlContent, boolean secret) {
        Map<String, String> values = new LinkedHashMap<>();
        if (yamlContent == null || yamlContent.isBlank()) {
            return values;
        }
        Object parsed = yaml.load(yamlContent);
        Map<String, Object> candidate = resolveKeyValueSection(parsed);
        candidate.forEach((key, value) -> {
            String normalized = normalizeScalar(value);
            values.put(String.valueOf(key), secret ? decodeSecretValue(normalized) : normalized);
        });
        return values;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveKeyValueSection(Object parsed) {
        if (!(parsed instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        if (map.containsKey("data")) {
            Object data = map.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                return new LinkedHashMap<>((Map<String, Object>) dataMap);
            }
        }
        if (map.containsKey("stringData")) {
            Object data = map.get("stringData");
            if (data instanceof Map<?, ?> dataMap) {
                return new LinkedHashMap<>((Map<String, Object>) dataMap);
            }
        }
        if (map.containsKey("kind")) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    private Map<String, Object> createBaseManifest(String name, String kind) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", "v1");
        manifest.put("kind", kind);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", name);
        metadata.put("namespace", "default");
        manifest.put("metadata", metadata);
        manifest.put("data", new LinkedHashMap<>());
        return manifest;
    }

    private String encodeSecretValue(String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String decodeSecretValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    private String normalizeScalar(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String str) {
            return str;
        }
        String dumped = yaml.dump(value);
        return dumped == null ? "" : dumped.trim();
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
        if (entry == null || !"ingress".equals(entry.kind)) {
            entry = new ManifestEntry("ingress", node.getId(), createBaseIngressManifest(node));
            manifests.put(node.getId(), entry);
        }
        Map<String, Object> manifest = entry.manifest;
        manifest.put("apiVersion", "networking.k8s.io/v1");
        manifest.put("kind", "Ingress");

        Map<String, Object> metadata = Optional.ofNullable(getMap(manifest, "metadata")).orElseGet(LinkedHashMap::new);
        metadata.put("name", node.getName());
        applyAnnotations(metadata, node.getAnnotations());
        manifest.put("metadata", metadata);

        updateIngressManifest(manifest, node);
    }

    private void updateIngressManifest(Map<String, Object> manifest, IngressDTO node) {
        Map<String, Object> spec = Optional.ofNullable(getMap(manifest, "spec")).orElseGet(LinkedHashMap::new);
        List<Map<String, Object>> rules = Optional.ofNullable(this.<Map<String, Object>>getList(spec, "rules"))
                .orElseGet(() -> new ArrayList<>());
        Map<String, Object> rule = rules.isEmpty() ? new LinkedHashMap<>() : rules.get(0);
        rule.put("host", node.getHost());
        Set<String> tlsHosts = new LinkedHashSet<>();
        if (node.getHost() != null && !node.getHost().isBlank()) {
            tlsHosts.add(node.getHost());
        }
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

        if (node.getTls() != null && !node.getTls().isBlank()) {
            Map<String, Object> tlsEntry = new LinkedHashMap<>();
            tlsEntry.put("secretName", node.getTls());
            if (!tlsHosts.isEmpty()) {
                tlsEntry.put("hosts", new ArrayList<>(tlsHosts));
            }
            spec.put("tls", List.of(tlsEntry));
        } else {
            spec.remove("tls");
        }

        manifest.put("spec", spec);
    }

    private void updateVirtualServiceManifestEntry(VirtualServiceDTO node, Map<String, ManifestEntry> manifests) {
        ManifestEntry entry = manifests.get(node.getId());
        if (entry == null || !"virtualservice".equals(entry.kind)) {
            entry = new ManifestEntry("virtualservice", node.getId(), createBaseVirtualServiceManifest(node));
            manifests.put(node.getId(), entry);
        }
        Map<String, Object> manifest = entry.manifest;
        manifest.put("apiVersion", "networking.istio.io/v1beta1");
        manifest.put("kind", "VirtualService");

        Map<String, Object> metadata = Optional.ofNullable(getMap(manifest, "metadata")).orElseGet(LinkedHashMap::new);
        metadata.put("name", node.getName());
        manifest.put("metadata", metadata);

        updateVirtualServiceManifest(manifest, node);
    }

    private void applyAnnotations(Map<String, Object> metadata, Map<String, String> annotations) {
        if (metadata == null) {
            return;
        }
        if (annotations == null || annotations.isEmpty()) {
            metadata.remove("annotations");
            return;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        annotations.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                normalized.put(key, value != null ? value : "");
            }
        });
        if (normalized.isEmpty()) {
            metadata.remove("annotations");
        } else {
            metadata.put("annotations", normalized);
        }
    }

    private Map<String, String> extractAnnotations(Map<String, Object> metadata) {
        Map<String, String> annotations = new LinkedHashMap<>();
        if (metadata == null) {
            return annotations;
        }
        Map<String, Object> rawAnnotations = getMap(metadata, "annotations");
        if (rawAnnotations == null) {
            return annotations;
        }
        rawAnnotations.forEach((key, value) -> {
            if (key != null) {
                annotations.put(key, value != null ? String.valueOf(value) : "");
            }
        });
        return annotations;
    }

    private String extractServiceShortName(String hostValue) {
        if (hostValue == null || hostValue.isBlank()) {
            return "";
        }
        int dotIndex = hostValue.indexOf('.');
        return dotIndex == -1 ? hostValue : hostValue.substring(0, dotIndex);
    }

    private void updateVirtualServiceManifest(Map<String, Object> manifest, VirtualServiceDTO node) {
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
        int containerPort = node.getContainerPort() != null ? node.getContainerPort() : 80;
        tempSpec.put("containers", List.of(Map.of("name", node.getName(), "image", "", "ports", List.of(Map.of("containerPort", containerPort)))));
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

    private Map<String, Object> createBaseVirtualServiceManifest(VirtualServiceDTO node) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", "networking.istio.io/v1beta1");
        manifest.put("kind", "VirtualService");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", node.getName());
        manifest.put("metadata", metadata);
        Map<String, Object> spec = new LinkedHashMap<>();
        String host = Optional.ofNullable(node.getHost()).orElse("example.com");
        spec.put("hosts", new ArrayList<>(List.of(host)));
        spec.put("http", new ArrayList<>());
        manifest.put("spec", spec);
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
            case "virtualservice", "istio" -> applyVirtualServiceHelmValues(manifest, entry.getName(), context);
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

        List<Map<String, Object>> tls = this.<Map<String, Object>>getList(spec, "tls");
        if (tls != null && !tls.isEmpty()) {
            Map<String, Object> tlsEntry = tls.get(0);
            String secretName = getString(tlsEntry, "secretName", null);
            if (secretName != null && !secretName.isBlank()) {
                context.values.put("tlsSecretName", secretName);
                tlsEntry.put("secretName", context.valueRef("tlsSecretName"));
            }
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
            case "ingress", "virtualservice", "istio" -> "ingresses";
            case "configmap" -> "configmaps";
            case "secret" -> "secrets";
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

    private Map<String, List<NodeDTO>> buildTargetsBySource(Map<String, NodeDTO> nodesById, List<LinkDTO> links) {
        Map<String, List<NodeDTO>> targetsBySource = new HashMap<>();
        if (links == null || links.isEmpty() || nodesById.isEmpty()) {
            return targetsBySource;
        }
        for (LinkDTO link : links) {
            NodeDTO target = nodesById.get(link.getTarget());
            if (target == null) {
                continue;
            }
            targetsBySource.computeIfAbsent(link.getSource(), key -> new ArrayList<>()).add(target);
        }
        return targetsBySource;
    }

    private void resolveIngressTargetsFromLinks(IngressDTO ingress, Map<String, List<NodeDTO>> targetsBySource) {
        if (ingress == null || ingress.getId() == null || targetsBySource.isEmpty()) {
            return;
        }
        List<NodeDTO> targets = targetsBySource.get(ingress.getId());
        if (targets == null || targets.isEmpty()) {
            return;
        }
        List<ServiceDTO> services = targets.stream()
                .filter(ServiceDTO.class::isInstance)
                .map(ServiceDTO.class::cast)
                .collect(Collectors.toList());
        if (services.isEmpty()) {
            return;
        }
        ServiceDTO primary = services.get(0);
        if (ingress.getServiceName() == null || ingress.getServiceName().isBlank()) {
            ingress.setServiceName(primary.getName());
        }
        if (ingress.getServicePort() <= 0 && primary.getPort() > 0) {
            ingress.setServicePort(primary.getPort());
        }
    }

    private void resolveVirtualServiceTargetsFromLinks(VirtualServiceDTO virtualService,
                                                       Map<String, List<NodeDTO>> targetsBySource) {
        if (virtualService == null || virtualService.getId() == null || targetsBySource.isEmpty()) {
            return;
        }
        List<NodeDTO> targets = targetsBySource.get(virtualService.getId());
        if (targets == null || targets.isEmpty()) {
            return;
        }
        List<ServiceDTO> services = targets.stream()
                .filter(ServiceDTO.class::isInstance)
                .map(ServiceDTO.class::cast)
                .collect(Collectors.toList());
        if (services.isEmpty()) {
            return;
        }
        ServiceDTO primary = services.get(0);
        if (virtualService.getServiceName() == null || virtualService.getServiceName().isBlank()) {
            virtualService.setServiceName(primary.getName());
        }
        if (virtualService.getServicePort() <= 0 && primary.getPort() > 0) {
            virtualService.setServicePort(primary.getPort());
        }
    }

    private LinkDTO createLink(String from, String to) {
        LinkDTO link = new LinkDTO();
        link.setSource(from);
        link.setTarget(to);
        return link;
    }

    private String generateNodeId(String kind, String resourceName) {
        String normalizedKind = Optional.ofNullable(kind)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .orElse("resource");
        String normalizedName = Optional.ofNullable(resourceName)
                .filter(value -> !value.isBlank())
                .orElse(normalizedKind + "-" + randomSuffix());
        String sanitizedName = normalizedName.replaceAll("[^a-zA-Z0-9._-]", "-").replaceAll("-{2,}", "-");
        if (sanitizedName.isBlank()) {
            sanitizedName = normalizedKind + "-" + randomSuffix();
        }
        return normalizedKind + ":" + sanitizedName;
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
        Map<String, String> labels = new LinkedHashMap<>();
        Set<String> referencedConfigResources = new LinkedHashSet<>();
        Set<Integer> containerPorts = new LinkedHashSet<>();
    }

    private static class DeploymentArtifacts {
        final DeploymentDTO deploymentNode;
        final DeploymentInfo info;

        DeploymentArtifacts(DeploymentDTO deploymentNode, DeploymentInfo info) {
            this.deploymentNode = deploymentNode;
            this.info = info;
        }
    }

    private static class ServiceInfo {
        final ServiceDTO node;
        final Map<String, String> selector;
        Map<String, Object> manifest;

        ServiceInfo(ServiceDTO node, Map<String, String> selector) {
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

    private static class VirtualServiceInfo {
        final String nodeId;
        final List<String> targetServiceNames;
        final String host;
        Map<String, Object> manifest;

        VirtualServiceInfo(String nodeId, List<String> targetServiceNames, String host) {
            this.nodeId = nodeId;
            this.targetServiceNames = targetServiceNames;
            this.host = host;
        }
    }

    private static class VirtualServiceArtifacts {
        final VirtualServiceDTO node;
        final VirtualServiceInfo info;

        VirtualServiceArtifacts(VirtualServiceDTO node, VirtualServiceInfo info) {
            this.node = node;
            this.info = info;
        }
    }
}
