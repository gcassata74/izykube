package com.izylife.izykube.services.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.ConfigEntryDTO;
import com.izylife.izykube.dto.cluster.ConfigEntrySensitivity;
import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.dto.cluster.ContainerRole;
import com.izylife.izykube.dto.cluster.CustomResourceDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.DeploymentWorkloadType;
import com.izylife.izykube.dto.cluster.AccessPolicyBindingStrategy;
import com.izylife.izykube.dto.cluster.AccessPolicyDTO;
import com.izylife.izykube.dto.cluster.AccessPolicyRuleDTO;
import com.izylife.izykube.dto.cluster.IngressDTO;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.SecretDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import com.izylife.izykube.dto.cluster.ServiceAccountDTO;
import com.izylife.izykube.dto.cluster.VirtualServiceDTO;
import com.izylife.izykube.enums.AssetType;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ClusterYamlService {

    private static final Logger log = LoggerFactory.getLogger(ClusterYamlService.class);

    private static final Map<String, String> ICON_MAP = Map.ofEntries(
            Map.entry("deployment", "assets/images/diagram/deployment.svg"),
            Map.entry("service", "assets/images/diagram/service.svg"),
            Map.entry("configmap", "assets/images/diagram/config-map.svg"),
            Map.entry("secret", "assets/images/diagram/secret.svg"),
            Map.entry("ingress", "assets/images/diagram/ingress.svg"),
            Map.entry("container", "assets/images/diagram/container.svg"),
            Map.entry("volume", "assets/images/diagram/volume.svg"),
            Map.entry("job", "assets/images/diagram/wrench.svg"),
            Map.entry("cr", "pi pi-sliders-h"),
            Map.entry("istio", "assets/images/diagram/istio.svg")
    );

    private final AssetRepository assetRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Yaml yaml;

    @Autowired
    public ClusterYamlService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
        DumperOptions options = initOptions();
        this.yaml = new Yaml(options);
    }

    public ClusterYamlService() {
        this.assetRepository = null;
        DumperOptions options = initOptions();
        this.yaml = new Yaml(options);
    }

    private DumperOptions initOptions() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        return options;
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

                if ("virtualservice".equals(kind) || "gateway".equals(kind) || "istio".equals(kind)) {
                    log.info("Skipping {} '{}' during import: routes must be configured manually", kind, name);
                    continue;
                }

                ManifestEntry manifestEntry = new ManifestEntry(kind, name, manifest);
                manifests.add(manifestEntry);

                switch (kind) {
                    case "configmap", "secret" -> nodes.add(buildConfigMapNode(name, manifest));
                    case "deployment" -> {
                        DeploymentArtifacts artifacts = buildDeploymentNodes(name, manifest, DeploymentWorkloadType.DEPLOYMENT);
                        nodes.add(artifacts.deploymentNode);
                        deploymentInfoMap.put(artifacts.deploymentNode.getId(), artifacts.info);
                    }
                    case "statefulset" -> {
                        DeploymentArtifacts artifacts = buildDeploymentNodes(name, manifest, DeploymentWorkloadType.STATEFULSET);
                        nodes.add(artifacts.deploymentNode);
                        deploymentInfoMap.put(artifacts.deploymentNode.getId(), artifacts.info);
                    }
                    case "daemonset" -> {
                        DeploymentArtifacts artifacts = buildDeploymentNodes(name, manifest, DeploymentWorkloadType.DAEMONSET);
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
                    default -> log.info("Skipping unsupported manifest kind: {}", kind);
                }
            }
        } catch (YAMLException ex) {
            throw new ClusterYamlException("Invalid YAML content: " + ex.getMessage(), ex);
        }

        linkConfigAndSecretResources(links, manifests, deploymentInfoMap);
        linkServicesToWorkloads(links, serviceInfoMap, deploymentInfoMap);
        linkIngressTargets(links, ingressInfoList, serviceInfoMap);

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

        String namespace = resolveNamespace(cluster);
        List<ManifestEntry> manifestEntries = extractManifestEntries(cluster.getDiagram());
        Map<String, ManifestEntry> manifestsByName = manifestEntries.stream()
                .collect(Collectors.toMap(ManifestEntry::getName, entry -> entry, (a, b) -> a, LinkedHashMap::new));

        List<NodeDTO> nodes = cluster.getNodes() != null ? cluster.getNodes() : List.of();
        nodes.forEach(node -> {
            if (node instanceof ServiceAccountDTO serviceAccount) {
                if (serviceAccount.getNamespace() == null || serviceAccount.getNamespace().isBlank()) {
                    serviceAccount.setNamespace(namespace);
                }
            } else if (node instanceof AccessPolicyDTO policy) {
                if (policy.getNamespace() == null || policy.getNamespace().isBlank()) {
                    policy.setNamespace(namespace);
                }
            } else {
                node.setNamespace(namespace);
            }
        });
        Map<String, NodeDTO> nodesById = nodes.stream()
                .filter(node -> node.getId() != null)
                .collect(Collectors.toMap(NodeDTO::getId, node -> node, (a, b) -> a, LinkedHashMap::new));
        Map<String, List<NodeDTO>> targetsBySource = buildTargetsBySource(nodesById, cluster.getLinks());
        Map<String, List<NodeDTO>> sourcesByTarget = buildSourcesByTarget(nodesById, cluster.getLinks());
        Map<String, String> statefulServiceSelectors = resolveStatefulServiceSelectors(cluster.getLinks(), nodesById);
        Map<String, String> statefulServiceNamesByDeployment = resolveStatefulServiceNames(cluster.getLinks(), nodesById);
        List<LinkDTO> links = cluster.getLinks() != null ? cluster.getLinks() : List.of();

        applyAccessPoliciesToWorkloads(nodesById, links, namespace);

        for (NodeDTO node : nodes) {
            if (node.getKind() == null) {
                continue;
            }
            node.setName(trimName(node.getName()));
            switch (node.getKind().toLowerCase(Locale.ROOT)) {
                case "configmap" -> updateConfigMapManifest((ConfigMapDTO) node, manifestsByName);
                case "secret" -> updateSecretManifest((SecretDTO) node, manifestsByName);
                case "deployment" -> updateDeploymentManifest((DeploymentDTO) node, manifestsByName, targetsBySource, sourcesByTarget, statefulServiceNamesByDeployment, links, nodesById);
                case "service" -> updateServiceManifest((ServiceDTO) node, manifestsByName, statefulServiceSelectors);
                case "serviceaccount" -> updateServiceAccountManifest((ServiceAccountDTO) node, manifestsByName);
                case "cr" -> updateCustomResourceManifest((CustomResourceDTO) node, manifestsByName);
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

        updateRbacManifests(manifestsByName, nodesById, links, namespace);

        return new ArrayList<>(manifestsByName.values());
    }

    private void applyAccessPoliciesToWorkloads(Map<String, NodeDTO> nodesById, List<LinkDTO> links, String namespace) {
        if (nodesById == null || links == null) {
            return;
        }

        List<AccessPolicyDTO> policies = nodesById.values().stream()
                .filter(node -> node instanceof AccessPolicyDTO)
                .map(node -> (AccessPolicyDTO) node)
                .toList();
        if (policies.isEmpty()) {
            return;
        }

        Map<String, List<LinkDTO>> linksByPolicy = links.stream()
                .filter(link -> link != null && link.getSource() != null && link.getTarget() != null)
                .collect(Collectors.groupingBy(LinkDTO::getSource, LinkedHashMap::new, Collectors.toList()));

        for (AccessPolicyDTO policy : policies) {
            if (isAccessPolicyRoleBindingNode(policy)) {
                continue;
            }
            String policyNs = resolveNamespace(policy);
            if (!Objects.equals(policyNs, namespace)) {
                throw new IllegalArgumentException("AccessPolicy '" + trimName(policy.getName()) + "' must be in the cluster namespace '" + namespace + "'");
            }
            validateAccessPolicyRules(policy);

            List<LinkDTO> connected = new ArrayList<>();
            connected.addAll(linksByPolicy.getOrDefault(policy.getId(), List.of()));
            connected.addAll(links.stream()
                    .filter(link -> link != null && Objects.equals(policy.getId(), link.getTarget()))
                    .toList());

            for (LinkDTO link : connected) {
                String otherId = Objects.equals(policy.getId(), link.getSource()) ? link.getTarget() : link.getSource();
                NodeDTO target = nodesById.get(otherId);
                if (target instanceof DeploymentDTO deployment) {
                    String workloadName = trimName(deployment.getName());
                    String saName = resolveAccessPolicyServiceAccountName(policy, workloadName, deployment.getId());
                    deployment.setServiceAccountName(saName);
                } else if (target != null) {
                    throw new IllegalArgumentException("AccessPolicy '" + trimName(policy.getName()) + "' cannot be linked to target kind '" + target.getKind() + "'");
                }
            }
        }
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
        ConfigMapDTO node = isSecret
                ? new SecretDTO(nodeId, name, yamlContent)
                : new ConfigMapDTO(nodeId, name, yamlContent);
        List<ConfigEntryDTO> configEntries = values.entrySet().stream()
                .map(entry -> {
                    ConfigEntryDTO dto = new ConfigEntryDTO();
                    dto.setKey(entry.getKey());
                    dto.setValue(entry.getValue());
                    dto.setSensitivity(isSecret ? ConfigEntrySensitivity.SECRET : ConfigEntrySensitivity.PLAIN);
                    return dto;
                })
                .collect(Collectors.toList());
        node.setEntries(configEntries);
        return node;
    }

    private DeploymentArtifacts buildDeploymentNodes(String name, Map<String, Object> manifest, DeploymentWorkloadType workloadType) {
        Map<String, Object> spec = getMap(manifest, "spec");
        int replicas = getInt(spec, "replicas", 1);
        String strategy = Optional.ofNullable(getMap(spec, "strategy"))
                .map(strategyMap -> getString(strategyMap, "type", "RollingUpdate"))
                .orElse("RollingUpdate");

        DeploymentDTO deploymentNode = new DeploymentDTO(
                generateNodeId("deployment", name),
                name,
                replicas,
                strategy,
                "",
                80,
                workloadType == null ? DeploymentWorkloadType.DEPLOYMENT : workloadType
        );

        Map<String, Object> template = Optional.ofNullable(getMap(spec, "template")).orElseGet(LinkedHashMap::new);
        Map<String, Object> podSpec = getMap(template, "spec");
        Map<String, Object> podMetadata = getMap(template, "metadata");
        Map<String, Object> podAnnotations = getMap(podMetadata, "annotations");
        String inject = podAnnotations != null ? String.valueOf(podAnnotations.get("sidecar.istio.io/inject")) : null;
        if (inject != null && inject.equalsIgnoreCase("true")) {
            deploymentNode.setAddToMesh(true);
        } else {
            deploymentNode.setAddToMesh(false);
        }

        DeploymentInfo info = new DeploymentInfo();
        info.labels.putAll(LabelMatcher.normalize(getMap(podMetadata, "labels")));
        Map<String, Object> selector = getMap(spec, "selector");
        Map<String, Object> matchLabels = selector != null ? getMap(selector, "matchLabels") : null;
        info.labels.putAll(LabelMatcher.normalize(matchLabels));
        info.referencedConfigResources.addAll(extractConfigAndSecretReferences(podSpec));
        info.containerPorts.addAll(extractContainerPorts(podSpec));

        info.containerPorts.stream().findFirst().ifPresent(port -> deploymentNode.setContainerPort(port));
        resolveAssetIdFromPodSpec(podSpec).ifPresent(deploymentNode::setAssetId);

        return new DeploymentArtifacts(deploymentNode, info);
    }

    private Optional<String> resolveAssetIdFromPodSpec(Map<String, Object> podSpec) {
        if (podSpec == null || assetRepository == null) {
            return Optional.empty();
        }
        List<Map<String, Object>> containers = getList(podSpec, "containers");
        if (containers == null || containers.isEmpty()) {
            return Optional.empty();
        }
        for (Map<String, Object> container : containers) {
            String image = getString(container, "image", null);
            Optional<Asset> asset = findAssetForImage(image);
            if (asset.isPresent()) {
                return asset.map(Asset::getId);
            }
        }
        return Optional.empty();
    }

    private Optional<Asset> findAssetForImage(String imageRef) {
        if (assetRepository == null) {
            return Optional.empty();
        }
        String normalized = normalizeImageRef(imageRef);
        if (normalized == null) {
            return Optional.empty();
        }
        ImageRef parsed = parseImageRef(normalized);
        if (parsed != null && parsed.name() != null && parsed.version() != null) {
            Optional<Asset> byNameVersion = assetRepository
                    .findFirstByTypeAndNameIgnoreCaseAndVersionIgnoreCase(AssetType.IMAGE, parsed.name(), parsed.version());
            if (byNameVersion.isPresent()) {
                return byNameVersion;
            }
        }
        return assetRepository.findByTypeAndImageIgnoreCase(AssetType.IMAGE, normalized);
    }

    private String normalizeImageRef(String imageRef) {
        if (imageRef == null) {
            return null;
        }
        String trimmed = imageRef.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ImageRef parseImageRef(String imageRef) {
        if (imageRef == null || imageRef.isBlank()) {
            return null;
        }
        String ref = imageRef.trim();
        int digestIndex = ref.indexOf('@');
        if (digestIndex > -1) {
            ref = ref.substring(0, digestIndex);
        }
        int lastSlash = ref.lastIndexOf('/');
        int lastColon = ref.lastIndexOf(':');
        String namePart = ref;
        String version = "latest";
        if (lastColon > lastSlash) {
            version = ref.substring(lastColon + 1).trim();
            namePart = ref.substring(0, lastColon);
        }
        if (version.isEmpty()) {
            version = "latest";
        }
        String name = namePart;
        if (lastSlash >= 0 && lastSlash + 1 < namePart.length()) {
            name = namePart.substring(lastSlash + 1);
        }
        name = name == null ? null : name.trim();
        if (name == null || name.isEmpty()) {
            return null;
        }
        return new ImageRef(name, version, imageRef);
    }

    private record ImageRef(String name, String version, String normalized) {
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

        ServiceDTO node = new ServiceDTO(generateNodeId("service", name), trimName(name), type, port, nodePort, false, null);
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
                    String linkId = link.getId() != null && !link.getId().isBlank()
                            ? link.getId()
                            : link.getSource() + "->" + link.getTarget();
                    linkMap.put("id", linkId);
                    linkMap.put("from", link.getSource());
                    linkMap.put("to", link.getTarget());
                    linkMap.put("type", link.getType());
                    if (link.getContainerRole() != null) {
                        linkMap.put("containerRole", link.getContainerRole().name());
                    }
                    if (link.getNote() != null) {
                        linkMap.put("note", link.getNote());
                    }
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
        List<ConfigEntryDTO> entries = Optional.ofNullable(node.getEntries()).orElseGet(ArrayList::new);

        boolean hasPlainEntries = entries.stream()
                .anyMatch(entry -> entry != null && !ConfigEntrySensitivity.SECRET.equals(entry.getSensitivity()));

        boolean hasSecretEntries = entries.stream()
                .anyMatch(entry -> entry != null && ConfigEntrySensitivity.SECRET.equals(entry.getSensitivity()));

        String resourceName = resolveResourceName(node);

        manifests.entrySet().removeIf(entry ->
                entry.getKey().equals(node.getId()) || entry.getKey().startsWith(node.getId() + ":"));

        if (hasPlainEntries) {
            Map<String, Object> configMapManifest = buildKeyValueManifest(node, resourceName, false);
            manifests.put(node.getId() + ":configmap", new ManifestEntry("configmap", resourceName, configMapManifest));
        }

        if (hasSecretEntries) {
            Map<String, Object> secretManifest = buildKeyValueManifest(node, resourceName, true);
            manifests.put(node.getId() + ":secret", new ManifestEntry("secret", resourceName, secretManifest));
        }

        if (!hasPlainEntries && !hasSecretEntries) {
            boolean secretManifest = isSecretConfig(node);
            Map<String, Object> manifest = buildKeyValueManifest(node, resourceName, secretManifest);
            String manifestKind = secretManifest ? "secret" : "configmap";
            manifests.put(node.getId(), new ManifestEntry(manifestKind, resourceName, manifest));
        }
    }

    private void updateSecretManifest(SecretDTO node, Map<String, ManifestEntry> manifests) {
        String resourceName = resolveResourceName(node);
        Map<String, Object> manifest = buildKeyValueManifest(node, resourceName, true);
        manifests.put(node.getId(), new ManifestEntry("secret", resourceName, manifest));
    }

    private void updateServiceAccountManifest(ServiceAccountDTO node, Map<String, ManifestEntry> manifests) {
        if (node == null) {
            return;
        }
        String namespace = resolveNamespace(node);
        String name = Optional.ofNullable(trimName(node.getName()))
                .filter(n -> !n.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("ServiceAccount name is required"));
        validateDns1123Subdomain(name, "ServiceAccount");

        Map<String, Object> manifest = createBaseManifest(name, "ServiceAccount", namespace);
        manifest.put("apiVersion", "v1");
        manifest.put("kind", "ServiceAccount");

        Map<String, Object> metadata = new LinkedHashMap<>(Optional.ofNullable(getMap(manifest, "metadata"))
                .orElseGet(LinkedHashMap::new));
        metadata.put("name", name);
        metadata.put("namespace", namespace);
        if (node.getLabels() != null && !node.getLabels().isEmpty()) {
            metadata.put("labels", new LinkedHashMap<>(node.getLabels()));
        }
        if (node.getAnnotations() != null && !node.getAnnotations().isEmpty()) {
            metadata.put("annotations", new LinkedHashMap<>(node.getAnnotations()));
        }
        manifest.put("metadata", metadata);
        manifest.put("automountServiceAccountToken", Optional.ofNullable(node.getAutomountServiceAccountToken()).orElse(true));

        manifests.put(node.getId(), new ManifestEntry("serviceaccount", name, manifest));
    }

    private void updateCustomResourceManifest(CustomResourceDTO node, Map<String, ManifestEntry> manifests) {
        if (node == null) {
            return;
        }
        String group = Optional.ofNullable(node.getCrdGroup()).map(String::trim).orElse("");
        String version = Optional.ofNullable(node.getCrdVersion()).map(String::trim).orElse("");
        String kind = Optional.ofNullable(node.getCrdKind()).map(String::trim).orElse("");
        String name = Optional.ofNullable(trimName(node.getName())).orElse("");
        if (group.isBlank() || version.isBlank() || kind.isBlank()) {
            throw new IllegalArgumentException("Custom Resource requires CRD group, version and kind");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Custom Resource name is required");
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", group + "/" + version);
        manifest.put("kind", kind);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", name);
        boolean namespaced = !"cluster".equalsIgnoreCase(Optional.ofNullable(node.getCrdScope()).orElse("Namespaced"));
        if (namespaced) {
            metadata.put("namespace", resolveNamespace(node));
        }
        manifest.put("metadata", metadata);
        manifest.put("spec", node.getSpec() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(node.getSpec()));

        manifests.put(node.getId(), new ManifestEntry("cr", name, manifest));
    }

    private void updateRbacManifests(Map<String, ManifestEntry> manifests,
                                    Map<String, NodeDTO> nodesById,
                                    List<LinkDTO> links,
                                    String namespace) {
        if (manifests == null || nodesById == null) {
            return;
        }

        manifests.entrySet().removeIf(entry -> entry.getKey() != null && entry.getKey().contains(":rbac:"));

        List<AccessPolicyDTO> policies = nodesById.values().stream()
                .filter(node -> node instanceof AccessPolicyDTO)
                .map(node -> (AccessPolicyDTO) node)
                .toList();
        if (policies.isEmpty()) {
            return;
        }

        Map<String, String> seenRoleNames = new LinkedHashMap<>();
        Map<String, String> usedRoleBindingNames = new LinkedHashMap<>();
        Map<String, String> usedServiceAccountNames = new LinkedHashMap<>();

        for (AccessPolicyDTO policy : policies) {
            String policyName = trimName(policy.getName());
            if (policyName == null || policyName.isBlank()) {
                throw new IllegalArgumentException("AccessPolicy name is required");
            }
            String policyNamespace = resolveNamespace(policy);
            if (!Objects.equals(policyNamespace, namespace)) {
                throw new IllegalArgumentException("AccessPolicy '" + policyName + "' must be in the cluster namespace '" + namespace + "'");
            }

            boolean roleBindingNode = isAccessPolicyRoleBindingNode(policy);
            if (!roleBindingNode) {
                validateAccessPolicyRules(policy);
            }

            String bindingKind = resolveAccessPolicyBindingKind(policy);
            if (roleBindingNode) {
                String bindingName = sanitizeDnsLabel(policyName, 56) + "-rb";
                validateDns1123Label(bindingName, bindingKind);
                RoleBindingRefs refs = resolveAccessPolicyRoleBindingRefs(policy, nodesById, links);
                if ("ClusterRoleBinding".equals(bindingKind) && !"ClusterRole".equals(refs.roleRefKind())) {
                    throw new IllegalArgumentException("RoleBinding '" + policyName + "' cannot use ClusterRoleBinding with Role. Select ClusterRole.");
                }

                Map<String, Object> roleBinding = createBaseManifest(bindingName, bindingKind, namespace);
                roleBinding.put("apiVersion", "rbac.authorization.k8s.io/v1");
                roleBinding.put("kind", bindingKind);
                if ("ClusterRoleBinding".equals(bindingKind)) {
                    roleBinding.remove("metadata");
                    roleBinding.put("metadata", Map.of("name", bindingName));
                }
                roleBinding.put("subjects", List.of(Map.of(
                        "kind", "ServiceAccount",
                        "name", refs.serviceAccountName(),
                        "namespace", namespace
                )));
                roleBinding.put("roleRef", Map.of(
                        "apiGroup", "rbac.authorization.k8s.io",
                        "kind", refs.roleRefKind(),
                        "name", refs.roleRefName()
                ));
                manifests.put(policy.getId() + ":rbac:binding", new ManifestEntry("rolebinding", bindingName, roleBinding));
                continue;
            }

            String roleKind = resolveAccessPolicyRoleKind(policy);
            String roleName = sanitizeDnsLabel(policyName, 63);
            String roleScope = "ClusterRole".equals(roleKind) ? "cluster" : namespace;
            String existingPolicyId = seenRoleNames.putIfAbsent(roleScope + ":" + roleName, policy.getId());
            if (existingPolicyId != null && !existingPolicyId.equals(policy.getId())) {
                throw new IllegalArgumentException("Duplicate AccessPolicy name '" + policyName + "' for " + roleKind);
            }

            Map<String, Object> role = createBaseManifest(roleName, roleKind, namespace);
            role.put("apiVersion", "rbac.authorization.k8s.io/v1");
            role.put("kind", roleKind);
            if ("ClusterRole".equals(roleKind)) {
                role.remove("metadata");
                role.put("metadata", Map.of("name", roleName));
            }
            role.put("rules", policy.getRules().stream().filter(Objects::nonNull).map(this::toRoleRule).toList());
            manifests.put(policy.getId() + ":rbac:role", new ManifestEntry("role", roleName, role));

            List<LinkDTO> connectedLinks = Optional.ofNullable(links).orElse(List.of()).stream()
                    .filter(link -> link != null && link.getSource() != null && link.getTarget() != null)
                    .filter(link -> Objects.equals(policy.getId(), link.getSource()) || Objects.equals(policy.getId(), link.getTarget()))
                    .toList();

            Set<String> targetIds = new LinkedHashSet<>();
            for (LinkDTO link : connectedLinks) {
                if (link == null) {
                    continue;
                }
                String otherId = Objects.equals(policy.getId(), link.getSource()) ? link.getTarget() : link.getSource();
                if (otherId != null) {
                    targetIds.add(otherId);
                }
            }

            if (targetIds.isEmpty()) {
                throw new IllegalArgumentException("AccessPolicy '" + policyName + "' is not applied to any target");
            }

            for (String targetId : targetIds) {
                NodeDTO target = nodesById.get(targetId);
                if (target == null) {
                    throw new IllegalArgumentException("AccessPolicy '" + policyName + "' references missing target node: " + targetId);
                }

                if (target instanceof DeploymentDTO deployment) {
                    String workloadName = trimName(deployment.getName());
                    String saName = resolveAccessPolicyServiceAccountName(policy, workloadName, deployment.getId());
                    String saKey = namespace + ":" + saName;
                    if (!usedServiceAccountNames.containsKey(saKey)) {
                        Map<String, Object> saManifest = createBaseManifest(saName, "ServiceAccount", namespace);
                        saManifest.put("apiVersion", "v1");
                        saManifest.put("kind", "ServiceAccount");
                        manifests.put(policy.getId() + ":rbac:sa:" + saName, new ManifestEntry("serviceaccount", saName, saManifest));
                        usedServiceAccountNames.put(saKey, policy.getId());
                    }

                    String bindingName = sanitizeDnsLabel(policyName, 30) + "-" + sanitizeDnsLabel(workloadName, 24) + "-rb";
                    if (usedRoleBindingNames.containsKey(bindingName) && !Objects.equals(usedRoleBindingNames.get(bindingName), targetId)) {
                        bindingName = ensureDnsLabelSuffix(bindingName, shortId(targetId), 63);
                    }
                    usedRoleBindingNames.putIfAbsent(bindingName, targetId);
                    validateDns1123Label(bindingName, bindingKind);
                    if ("ClusterRoleBinding".equals(bindingKind) && !"ClusterRole".equals(roleKind)) {
                        throw new IllegalArgumentException("AccessPolicy '" + policyName + "' cannot use ClusterRoleBinding with Role. Select ClusterRole.");
                    }

                    Map<String, Object> roleBinding = createBaseManifest(bindingName, bindingKind, namespace);
                    roleBinding.put("apiVersion", "rbac.authorization.k8s.io/v1");
                    roleBinding.put("kind", bindingKind);
                    if ("ClusterRoleBinding".equals(bindingKind)) {
                        roleBinding.remove("metadata");
                        roleBinding.put("metadata", Map.of("name", bindingName));
                    }
                    roleBinding.put("subjects", List.of(Map.of(
                            "kind", "ServiceAccount",
                            "name", saName,
                            "namespace", namespace
                    )));
                    roleBinding.put("roleRef", Map.of(
                            "apiGroup", "rbac.authorization.k8s.io",
                            "kind", roleKind,
                            "name", roleName
                    ));
                    manifests.put(policy.getId() + ":rbac:binding:" + targetId, new ManifestEntry("rolebinding", bindingName, roleBinding));
                } else if (target instanceof AccessPolicyDTO targetPolicy && isAccessPolicyRoleBindingNode(targetPolicy)) {
                    // Ignore reverse RoleBinding -> Role references while generating Role resources.
                    continue;
                } else {
                    throw new IllegalArgumentException("AccessPolicy '" + policyName + "' cannot be linked to target kind '" + target.getKind() + "'");
                }
            }
        }
    }

    private Map<String, Object> toRoleRule(AccessPolicyRuleDTO rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<String> apiGroups = Optional.ofNullable(rule.getApiGroups()).orElse(List.of("")).stream()
                .map(value -> value == null ? "" : value.trim())
                .toList();
        List<String> resources = Optional.ofNullable(rule.getResources()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toList();
        List<String> verbs = Optional.ofNullable(rule.getVerbs()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toList();
        map.put("apiGroups", apiGroups.isEmpty() ? List.of("") : apiGroups);
        map.put("resources", resources);
        map.put("verbs", verbs);
        List<String> resourceNames = Optional.ofNullable(rule.getResourceNames()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toList();
        if (!resourceNames.isEmpty()) {
            map.put("resourceNames", resourceNames);
        }
        return map;
    }

    private void validateAccessPolicyRules(AccessPolicyDTO policy) {
        List<AccessPolicyRuleDTO> rules = Optional.ofNullable(policy.getRules()).orElse(List.of());
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("AccessPolicy '" + trimName(policy.getName()) + "' must define at least one rule");
        }
        int idx = 0;
        for (AccessPolicyRuleDTO rule : rules) {
            idx++;
            if (rule == null) {
                continue;
            }
            List<String> resources = Optional.ofNullable(rule.getResources()).orElse(List.of()).stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).toList();
            List<String> verbs = Optional.ofNullable(rule.getVerbs()).orElse(List.of()).stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).toList();
            if (resources.isEmpty()) {
                throw new IllegalArgumentException("AccessPolicy '" + trimName(policy.getName()) + "' rule #" + idx + " must include at least one resource");
            }
            if (verbs.isEmpty()) {
                throw new IllegalArgumentException("AccessPolicy '" + trimName(policy.getName()) + "' rule #" + idx + " must include at least one verb");
            }
        }
    }

    private String resolveAccessPolicyServiceAccountName(AccessPolicyDTO policy, String workloadName, String workloadId) {
        String policyName = trimName(policy.getName());
        String safeWorkload = workloadName == null ? "" : workloadName;
        AccessPolicyBindingStrategy strategy = Optional.ofNullable(policy.getTargetBindingStrategy())
                .orElse(AccessPolicyBindingStrategy.WORKLOAD_SA_PER_WORKLOAD);

        String name = switch (strategy) {
            case WORKLOAD_SA_PER_WORKLOAD -> sanitizeDnsLabel(safeWorkload + "-sa", 63);
            case WORKLOAD_SA_PER_POLICY -> sanitizeDnsLabel(policyName + "-sa", 63);
            case WORKLOAD_SA_EXPLICIT_REFERENCE -> {
                String explicit = trimName(policy.getExistingServiceAccountName());
                if (explicit == null || explicit.isBlank()) {
                    throw new IllegalArgumentException("AccessPolicy '" + policyName + "' requires existingServiceAccountName when using WORKLOAD_SA_EXPLICIT_REFERENCE");
                }
                yield sanitizeDnsLabel(explicit, 63);
            }
        };

        if (name.length() > 63) {
            name = ensureDnsLabelSuffix(name, shortId(workloadId), 63);
        }
        validateDns1123Label(name, "ServiceAccount");
        return name;
    }

    private String resolveAccessPolicyRoleKind(AccessPolicyDTO policy) {
        String raw = trimName(policy != null ? policy.getRoleKind() : null);
        return raw != null && raw.equalsIgnoreCase("ClusterRole") ? "ClusterRole" : "Role";
    }

    private String resolveAccessPolicyBindingKind(AccessPolicyDTO policy) {
        String raw = trimName(policy != null ? policy.getBindingKind() : null);
        return raw != null && raw.equalsIgnoreCase("ClusterRoleBinding") ? "ClusterRoleBinding" : "RoleBinding";
    }

    private RoleBindingRefs resolveAccessPolicyRoleBindingRefs(
            AccessPolicyDTO bindingPolicy,
            Map<String, NodeDTO> nodesById,
            List<LinkDTO> links
    ) {
        AccessPolicyDTO linkedRole = null;
        ServiceAccountDTO linkedServiceAccount = null;
        String bindingId = trimName(bindingPolicy != null ? bindingPolicy.getId() : null);
        if (bindingId == null || bindingId.isBlank()) {
            throw new IllegalArgumentException("RoleBinding node id is required");
        }

        for (LinkDTO link : Optional.ofNullable(links).orElse(List.of())) {
            if (link == null || link.getSource() == null || link.getTarget() == null) {
                continue;
            }
            if (!Objects.equals(bindingId, link.getSource()) && !Objects.equals(bindingId, link.getTarget())) {
                continue;
            }
            String otherId = Objects.equals(bindingId, link.getSource()) ? link.getTarget() : link.getSource();
            NodeDTO other = nodesById.get(otherId);
            if (other == null) {
                throw new IllegalArgumentException("RoleBinding '" + trimName(bindingPolicy.getName()) + "' references missing target node: " + otherId);
            }
            if (other instanceof ServiceAccountDTO sa) {
                if (linkedServiceAccount != null && !Objects.equals(linkedServiceAccount.getId(), sa.getId())) {
                    throw new IllegalArgumentException("RoleBinding '" + trimName(bindingPolicy.getName()) + "' must link to exactly one ServiceAccount");
                }
                linkedServiceAccount = sa;
                continue;
            }
            if (other instanceof AccessPolicyDTO roleCandidate && !isAccessPolicyRoleBindingNode(roleCandidate)) {
                if (linkedRole != null && !Objects.equals(linkedRole.getId(), roleCandidate.getId())) {
                    throw new IllegalArgumentException("RoleBinding '" + trimName(bindingPolicy.getName()) + "' must link to exactly one Role");
                }
                linkedRole = roleCandidate;
            }
        }

        if (linkedServiceAccount == null || trimName(linkedServiceAccount.getName()) == null) {
            throw new IllegalArgumentException("RoleBinding '" + trimName(bindingPolicy.getName()) + "' requires one linked ServiceAccount");
        }
        if (linkedRole == null || trimName(linkedRole.getName()) == null) {
            throw new IllegalArgumentException("RoleBinding '" + trimName(bindingPolicy.getName()) + "' requires one linked Role");
        }

        String roleRefKind = resolveAccessPolicyRoleKind(linkedRole);
        String roleRefName = sanitizeDnsLabel(trimName(linkedRole.getName()), 63);
        String serviceAccountName = sanitizeDnsLabel(trimName(linkedServiceAccount.getName()), 63);
        return new RoleBindingRefs(roleRefName, roleRefKind, serviceAccountName);
    }

    private String resolveAccessPolicyRoleRefKind(AccessPolicyDTO policy) {
        String raw = trimName(policy != null ? policy.getRoleRefKind() : null);
        return raw != null && raw.equalsIgnoreCase("ClusterRole") ? "ClusterRole" : "Role";
    }

    private boolean isAccessPolicyRoleBindingNode(AccessPolicyDTO policy) {
        String raw = trimName(policy != null ? policy.getRbacNodeType() : null);
        return raw != null && raw.equalsIgnoreCase("ROLEBINDING");
    }

    private record RoleBindingRefs(String roleRefName, String roleRefKind, String serviceAccountName) {}

    private String sanitizeDnsLabel(String raw, int maxLen) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9-]+", "-");
        value = value.replaceAll("^-+", "");
        value = value.replaceAll("-+$", "");
        value = value.replaceAll("-{2,}", "-");
        if (value.isEmpty()) {
            value = "rbac";
        }
        if (value.length() > maxLen) {
            value = value.substring(0, maxLen);
            value = value.replaceAll("-+$", "");
        }
        if (value.isEmpty()) {
            value = "rbac";
        }
        return value;
    }

    private String ensureDnsLabelSuffix(String base, String suffix, int maxLen) {
        String normalized = sanitizeDnsLabel(base, maxLen);
        String safeSuffix = sanitizeDnsLabel(suffix, 12);
        int available = Math.max(1, maxLen - safeSuffix.length() - 1);
        String prefix = sanitizeDnsLabel(normalized, available);
        return prefix + "-" + safeSuffix;
    }

    private void validateDns1123Label(String name, String resourceType) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(resourceType + " name is required");
        }
        if (name.length() > 63) {
            throw new IllegalArgumentException(resourceType + " name must be <= 63 characters");
        }
        if (!name.matches("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")) {
            throw new IllegalArgumentException(resourceType + " name must be a valid DNS-1123 label (lowercase alphanumeric, '-', start/end alphanumeric)");
        }
    }

    private String shortId(String id) {
        if (id == null) {
            return "sa";
        }
        String normalized = id.replaceAll("[^a-zA-Z0-9]+", "");
        if (normalized.length() <= 6) {
            return normalized.isBlank() ? "sa" : normalized.toLowerCase(Locale.ROOT);
        }
        return normalized.substring(0, 6).toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> buildKeyValueManifest(ConfigMapDTO node, String resourceName, boolean secret) {
        String namespace = resolveNamespace(node);
        Map<String, Object> manifest = Optional.ofNullable(loadManifestFromYaml(node.getYaml()))
                .map(this::deepCopy)
                .orElseGet(() -> createBaseManifest(resourceName, secret ? "Secret" : "ConfigMap", namespace));

        manifest.put("apiVersion", manifest.getOrDefault("apiVersion", "v1"));
        manifest.put("kind", secret ? "Secret" : "ConfigMap");

        Map<String, Object> metadata = new LinkedHashMap<>(Optional.ofNullable(getMap(manifest, "metadata"))
                .orElseGet(LinkedHashMap::new));
        metadata.putIfAbsent("name", resourceName);
        metadata.putIfAbsent("namespace", namespace);
        manifest.put("metadata", metadata);

        Map<String, String> values = extractPlainKeyValueData(node.getYaml(), secret);
        if (values.isEmpty()) {
            values = extractValuesFromEntries(node, secret);
        }
        Map<String, Object> dataSection = new LinkedHashMap<>();
        values.forEach((key, value) -> dataSection.put(key, secret ? encodeSecretValue(value) : value));
        manifest.put("data", dataSection);
        if (secret) {
            manifest.remove("stringData");
        }
        return manifest;
    }

    private String resolveResourceName(ConfigMapDTO node) {
        String yamlName = Optional.ofNullable(loadManifestFromYaml(node.getYaml()))
                .map(manifest -> getMap(manifest, "metadata"))
                .map(meta -> getString(meta, "name", null))
                .map(this::trimName)
                .orElse(null);
        if (yamlName != null && !yamlName.isBlank()) {
            return yamlName;
        }
        return Optional.ofNullable(trimName(node.getName())).filter(name -> !name.isBlank()).orElse(node.getId());
    }

    private void applyPrimaryContainerSpec(DeploymentDTO node, Map<String, Object> templateSpec) {
        if (templateSpec == null) {
            return;
        }
        List<Map<String, Object>> containers = this.<Map<String, Object>>getList(templateSpec, "containers");
        if (containers == null) {
            containers = new ArrayList<>();
            templateSpec.put("containers", containers);
        }
        Map<String, Object> primary = containers.isEmpty() ? new LinkedHashMap<>() : containers.get(0);
        primary.put("name", Optional.ofNullable(trimName(node.getName())).filter(name -> !name.isBlank()).orElse(node.getId()));
        String image = resolveAssetImage(node);
        if (image != null && !image.isBlank()) {
            primary.put("image", image);
        }
        int port = node.getContainerPort() != null && node.getContainerPort() > 0 ? node.getContainerPort() : 80;
        List<Map<String, Object>> ports = this.<Map<String, Object>>getList(primary, "ports");
        if (ports == null) {
            ports = new ArrayList<>();
            primary.put("ports", ports);
        }
        Map<String, Object> primaryPort = ports.isEmpty() ? new LinkedHashMap<>() : ports.get(0);
        primaryPort.put("containerPort", port);
        if (ports.isEmpty()) {
            ports.add(primaryPort);
        }
        if (containers.isEmpty()) {
            containers.add(primary);
        }
    }

    private String resolveAssetImage(DeploymentDTO node) {
        if (node == null || node.getAssetId() == null || node.getAssetId().isBlank()) {
            return "";
        }
        if (assetRepository == null) {
            return "";
        }
        try {
            return assetRepository.findById(node.getAssetId())
                    .map(Asset::getImage)
                    .orElse("");
        } catch (Exception ex) {
            log.warn("Unable to resolve asset {} for deployment {}: {}", node.getAssetId(), node.getName(), ex.getMessage());
            return "";
        }
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

    private Map<String, Object> createBaseManifest(String name, String kind, String namespace) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", "v1");
        manifest.put("kind", kind);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", name);
        metadata.put("namespace", namespace);
        manifest.put("metadata", metadata);
        manifest.put("data", new LinkedHashMap<>());
        return manifest;
    }

    private Map<String, String> extractValuesFromEntries(ConfigMapDTO node, boolean secret) {
        List<ConfigEntryDTO> entries = node.getEntries();
        Map<String, String> values = new LinkedHashMap<>();
        if (entries == null || entries.isEmpty()) {
            return values;
        }
        for (ConfigEntryDTO entry : entries) {
            if (entry == null || entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            boolean isSecretEntry = ConfigEntrySensitivity.SECRET.equals(entry.getSensitivity());
            if (secret) {
                if (!isSecretEntry) {
                    continue;
                }
                values.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            } else {
                if (isSecretEntry) {
                    continue;
                }
                values.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return values;
    }

    private boolean isSecretConfig(ConfigMapDTO config) {
        return detectBundleSensitivity(config).hasSecretEntries();
    }

    private BundleSensitivity detectBundleSensitivity(ConfigMapDTO config) {
        if (config == null) {
            return new BundleSensitivity(false, false);
        }
        boolean hasPlainEntries = Optional.ofNullable(config.getEntries())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .anyMatch(entry -> !ConfigEntrySensitivity.SECRET.equals(entry.getSensitivity()));
        boolean hasSecretEntries = Optional.ofNullable(config.getEntries())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .anyMatch(entry -> ConfigEntrySensitivity.SECRET.equals(entry.getSensitivity()));
        boolean secretKind = "secret".equalsIgnoreCase(config.getKind());
        return new BundleSensitivity(hasPlainEntries, hasSecretEntries || secretKind);
    }

    private record BundleSensitivity(boolean hasPlainEntries, boolean hasSecretEntries) { }

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

    private String trimName(String name) {
        return name == null ? null : name.trim();
    }

    private Map<String, String> resolveStatefulServiceSelectors(List<LinkDTO> links, Map<String, NodeDTO> nodesById) {
        Map<String, String> selectors = new LinkedHashMap<>();
        if (links == null || links.isEmpty() || nodesById == null || nodesById.isEmpty()) {
            return selectors;
        }
        for (LinkDTO link : links) {
            NodeDTO source = nodesById.get(link.getSource());
            NodeDTO target = nodesById.get(link.getTarget());
            if (source == null || target == null) {
                continue;
            }
            if (isServiceToStateful(source, target)) {
                selectors.put(source.getId(), trimName(target.getName()));
            }
            if (isServiceToStateful(target, source)) {
                selectors.put(target.getId(), trimName(source.getName()));
            }
        }
        return selectors;
    }

    private Map<String, String> resolveStatefulServiceNames(List<LinkDTO> links, Map<String, NodeDTO> nodesById) {
        Map<String, String> serviceNames = new LinkedHashMap<>();
        if (links == null || links.isEmpty() || nodesById == null || nodesById.isEmpty()) {
            return serviceNames;
        }
        for (LinkDTO link : links) {
            NodeDTO source = nodesById.get(link.getSource());
            NodeDTO target = nodesById.get(link.getTarget());
            if (source == null || target == null) {
                continue;
            }
            if (isServiceToStateful(source, target)) {
                serviceNames.put(target.getId(), trimName(source.getName()));
            }
            if (isServiceToStateful(target, source)) {
                serviceNames.put(source.getId(), trimName(target.getName()));
            }
        }
        return serviceNames;
    }

    private boolean isServiceToStateful(NodeDTO maybeService, NodeDTO maybeWorkload) {
        if (!(maybeService instanceof ServiceDTO) || !(maybeWorkload instanceof DeploymentDTO deployment)) {
            return false;
        }
        return deployment.resolveWorkloadType() == DeploymentWorkloadType.STATEFULSET;
    }

    private void updateDeploymentManifest(DeploymentDTO node,
                                          Map<String, ManifestEntry> manifests,
                                          Map<String, List<NodeDTO>> targetsBySource,
                                          Map<String, List<NodeDTO>> sourcesByTarget,
                                          Map<String, String> statefulServiceNamesByDeployment,
                                          List<LinkDTO> links,
                                          Map<String, NodeDTO> nodesById) {
        ManifestEntry entry = manifests.get(node.getId());
        if (entry == null) {
            entry = new ManifestEntry("deployment", node.getId(), createBaseDeploymentManifest(node));
            manifests.put(node.getId(), entry);
        }
        Map<String, Object> manifest = entry.manifest;
        manifest.put("apiVersion", manifest.getOrDefault("apiVersion", "apps/v1"));
        DeploymentWorkloadType workloadType = node.resolveWorkloadType();
        String kind = switch (workloadType) {
            case STATEFULSET -> "StatefulSet";
            case DAEMONSET -> "DaemonSet";
            default -> "Deployment";
        };
        manifest.put("kind", kind);
        Map<String, Object> metadata = Optional.ofNullable(getMap(manifest, "metadata")).orElseGet(LinkedHashMap::new);
        String sanitizedName = trimName(node.getName());
        metadata.put("name", sanitizedName);
        metadata.putIfAbsent("namespace", resolveNamespace(node));
        manifest.put("metadata", metadata);

        Map<String, Object> spec = new LinkedHashMap<>(Optional.ofNullable(getMap(manifest, "spec"))
                .orElseGet(LinkedHashMap::new));
        if (workloadType == DeploymentWorkloadType.DAEMONSET) {
            spec.remove("replicas");
        } else {
            spec.put("replicas", node.getReplicas());
        }
        if (workloadType == DeploymentWorkloadType.STATEFULSET) {
            spec.remove("strategy");
            Map<String, Object> updateStrategy = new LinkedHashMap<>(Optional.ofNullable(getMap(spec, "updateStrategy"))
                    .orElseGet(LinkedHashMap::new));
            updateStrategy.put("type", node.getStrategyType());
            spec.put("updateStrategy", updateStrategy);
        } else {
            Map<String, Object> strategy = new LinkedHashMap<>(Optional.ofNullable(getMap(spec, "strategy"))
                    .orElseGet(LinkedHashMap::new));
            strategy.put("type", node.getStrategyType());
            spec.put("strategy", strategy);
            spec.remove("updateStrategy");
        }
        Map<String, Object> template = new LinkedHashMap<>(Optional.ofNullable(getMap(spec, "template"))
                .orElseGet(LinkedHashMap::new));
        Map<String, Object> templateMetadata = new LinkedHashMap<>(Optional.ofNullable(getMap(template, "metadata"))
                .orElseGet(LinkedHashMap::new));
        Map<String, Object> podAnnotations = new LinkedHashMap<>(Optional.ofNullable(getMap(templateMetadata, "annotations"))
                .orElseGet(LinkedHashMap::new));
        podAnnotations.put("sidecar.istio.io/inject", node.isAddToMesh() ? "true" : "false");
        Map<String, Object> podLabels = new LinkedHashMap<>(Optional.ofNullable(getMap(templateMetadata, "labels"))
                .orElseGet(LinkedHashMap::new));
        if (node.isAddToMesh()) {
            podLabels.put("sidecar.istio.io/inject", "true");
        } else {
            podLabels.remove("sidecar.istio.io/inject");
        }
        if (podAnnotations.isEmpty()) {
            templateMetadata.remove("annotations");
        } else {
            templateMetadata.put("annotations", podAnnotations);
        }
        if (!podLabels.isEmpty()) {
            templateMetadata.put("labels", podLabels);
        }
        template.put("metadata", templateMetadata);
        Map<String, Object> templateSpec = new LinkedHashMap<>(Optional.ofNullable(getMap(template, "spec"))
                .orElseGet(LinkedHashMap::new));
        applyPrimaryContainerSpec(node, templateSpec);
        applyAttachedContainerSpecs(node, templateSpec, targetsBySource, sourcesByTarget, links);
        applyLinkedConfigReferences(node, templateSpec, targetsBySource, sourcesByTarget);
        applyServiceAccountBinding(node, templateSpec, nodesById, links);
        template.put("spec", templateSpec);
        Map<String, Object> selector = new LinkedHashMap<>(Optional.ofNullable(getMap(spec, "selector")).orElseGet(LinkedHashMap::new));
        Map<String, Object> matchLabels = new LinkedHashMap<>(Optional.ofNullable(getMap(selector, "matchLabels")).orElseGet(LinkedHashMap::new));
        matchLabels.put("app", sanitizedName);
        selector.put("matchLabels", matchLabels);
        spec.put("selector", selector);
        if (workloadType == DeploymentWorkloadType.STATEFULSET) {
            String linkedServiceName = Optional.ofNullable(statefulServiceNamesByDeployment.get(node.getId()))
                    .map(this::trimName)
                    .filter(name -> !name.isBlank())
                    .orElse(sanitizedName);
            spec.put("serviceName", linkedServiceName);
        } else {
            spec.remove("serviceName");
        }
        spec.put("template", template);
        manifest.put("spec", spec);
    }

    private void applyServiceAccountBinding(DeploymentDTO node,
                                           Map<String, Object> templateSpec,
                                           Map<String, NodeDTO> nodesById,
                                           List<LinkDTO> links) {
        if (node == null || templateSpec == null || nodesById == null) {
            return;
        }

        String explicitName = Optional.ofNullable(trimName(node.getServiceAccountName())).orElse("");
        if (!explicitName.isBlank()) {
            validateDns1123Subdomain(explicitName, "ServiceAccount");
            templateSpec.put("serviceAccountName", explicitName);
            return;
        }

        String serviceAccountId = Optional.ofNullable(node.getServiceAccountRef()).map(String::trim).orElse("");
        if (serviceAccountId.isBlank()) {
            List<LinkDTO> bindings = Optional.ofNullable(links).orElse(List.of())
                    .stream()
                    .filter(link -> link != null
                            && "serviceAccountBinding".equalsIgnoreCase(link.getType())
                            && Objects.equals(node.getId(), link.getTarget()))
                    .toList();
            if (bindings.size() > 1) {
                throw new IllegalArgumentException("Workload " + node.getName() + " references multiple ServiceAccounts; only one is allowed");
            }
            if (bindings.size() == 1) {
                serviceAccountId = Optional.ofNullable(bindings.get(0).getSource()).map(String::trim).orElse("");
                node.setServiceAccountRef(serviceAccountId);
            }
        }

        if (serviceAccountId.isBlank()) {
            templateSpec.remove("serviceAccountName");
            return;
        }

        NodeDTO resolved = nodesById.get(serviceAccountId);
        if (!(resolved instanceof ServiceAccountDTO sa)) {
            throw new IllegalArgumentException("Workload " + node.getName() + " references missing ServiceAccount: " + serviceAccountId);
        }

        String saNamespace = resolveNamespace(sa);
        String workloadNamespace = resolveNamespace(node);
        if (!Objects.equals(saNamespace, workloadNamespace)) {
            throw new IllegalArgumentException("ServiceAccount " + sa.getName() + " must be in the same namespace as workload " + node.getName());
        }

        String saName = trimName(sa.getName());
        if (saName == null || saName.isBlank()) {
            throw new IllegalArgumentException("ServiceAccount name is required for workload " + node.getName());
        }
        validateDns1123Subdomain(saName);

        templateSpec.put("serviceAccountName", saName);
    }

    private void validateDns1123Subdomain(String name) {
        validateDns1123Subdomain(name, "ServiceAccount");
    }

    private void validateDns1123Subdomain(String name, String resourceType) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(resourceType + " name is required");
        }
        if (name.length() > 253) {
            throw new IllegalArgumentException(resourceType + " name must be <= 253 characters");
        }
        if (!name.matches("^[a-z0-9]([a-z0-9-.]*[a-z0-9])?$")) {
            throw new IllegalArgumentException(resourceType + " name must be a valid DNS-1123 subdomain (lowercase alphanumeric, '-', '.', start/end alphanumeric)");
        }
    }

    private void applyAttachedContainerSpecs(DeploymentDTO node,
                                            Map<String, Object> templateSpec,
                                            Map<String, List<NodeDTO>> targetsBySource,
                                            Map<String, List<NodeDTO>> sourcesByTarget,
                                            List<LinkDTO> links) {
        if (node == null || node.getId() == null || templateSpec == null) {
            return;
        }

        List<ContainerAttachment> attachments = collectAttachedContainers(node, targetsBySource, sourcesByTarget, links);
        if (attachments.isEmpty()) {
            templateSpec.remove("initContainers");
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Classifying linked containers for workload {} (id={}): linkedContainerNodes={}",
                    node.getName(),
                    node.getId(),
                    attachments.size());
            for (ContainerAttachment attachment : attachments) {
                LinkDTO link = attachment.link;
                ContainerDTO container = attachment.container;
                log.debug("Linked container: id={} name={} role={} linkType={} linkRole={}",
                        container != null ? container.getId() : null,
                        container != null ? container.getName() : null,
                        attachment.role,
                        link != null ? link.getType() : null,
                        link != null ? link.getContainerRole() : null);
            }
        }

        List<Map<String, Object>> mainContainers = this.<Map<String, Object>>getList(templateSpec, "containers");
        if (mainContainers == null) {
            mainContainers = new ArrayList<>();
            templateSpec.put("containers", mainContainers);
        }

        List<ContainerAttachment> initAttachments = attachments.stream()
                .filter(att -> att.role == ContainerRole.INIT)
                .sorted((a, b) -> compareByName(a.container, b.container))
                .toList();

        List<ContainerAttachment> sidecarAttachments = attachments.stream()
                .filter(att -> att.role == ContainerRole.SIDECAR)
                .sorted((a, b) -> compareByName(a.container, b.container))
                .toList();

        validateUniqueContainerNames(node, mainContainers, initAttachments, sidecarAttachments);

        if (initAttachments.isEmpty()) {
            templateSpec.remove("initContainers");
        } else {
            List<Map<String, Object>> initContainers = initAttachments.stream()
                    .map(att -> buildContainerSpec(att.container))
                    .collect(Collectors.toCollection(ArrayList::new));
            templateSpec.put("initContainers", initContainers);
        }

        List<Map<String, Object>> sidecars = sidecarAttachments.stream()
                .map(att -> buildContainerSpec(att.container))
                .collect(Collectors.toCollection(ArrayList::new));
        if (!sidecars.isEmpty()) {
            mainContainers.addAll(sidecars);
        }

        if (log.isDebugEnabled()) {
            log.debug("Container classification result for workload {} (id={}): initContainers={}, containers={}",
                    node.getName(),
                    node.getId(),
                    initAttachments.size(),
                    mainContainers.size());
        }
    }

    private List<ContainerAttachment> collectAttachedContainers(DeploymentDTO node,
                                                               Map<String, List<NodeDTO>> targetsBySource,
                                                               Map<String, List<NodeDTO>> sourcesByTarget,
                                                               List<LinkDTO> links) {
        Stream<NodeDTO> outgoing = Optional.ofNullable(targetsBySource.get(node.getId()))
                .orElse(List.of())
                .stream();
        Stream<NodeDTO> incoming = Optional.ofNullable(sourcesByTarget.get(node.getId()))
                .orElse(List.of())
                .stream();

        Map<String, ContainerAttachment> unique = new LinkedHashMap<>();
        Stream.concat(outgoing, incoming)
                .filter(Objects::nonNull)
                .filter(ContainerDTO.class::isInstance)
                .map(ContainerDTO.class::cast)
                .forEach(container -> {
                    LinkDTO link = findLinkBetween(links, node.getId(), container.getId());
                    if (link == null) {
                        return;
                    }
                    ContainerRole role = resolveContainerRole(link, container);
                    unique.putIfAbsent(container.getId(), new ContainerAttachment(container, role, link));
                });

        return new ArrayList<>(unique.values());
    }

    private ContainerRole resolveContainerRole(LinkDTO link, ContainerDTO container) {
        if (container != null && container.getRole() != null) {
            return container.getRole();
        }
        if (link != null && link.getContainerRole() != null) {
            return link.getContainerRole();
        }
        return ContainerRole.SIDECAR;
    }

    private LinkDTO findLinkBetween(List<LinkDTO> links, String a, String b) {
        if (links == null || links.isEmpty() || a == null || b == null) {
            return null;
        }
        for (LinkDTO link : links) {
            if (link == null) {
                continue;
            }
            if ((a.equals(link.getSource()) && b.equals(link.getTarget()))
                    || (a.equals(link.getTarget()) && b.equals(link.getSource()))) {
                return link;
            }
        }
        return null;
    }

    private int compareByName(ContainerDTO a, ContainerDTO b) {
        String nameA = Optional.ofNullable(a).map(ContainerDTO::getName).map(this::trimName).orElse("");
        String nameB = Optional.ofNullable(b).map(ContainerDTO::getName).map(this::trimName).orElse("");
        int cmp = nameA.compareToIgnoreCase(nameB);
        if (cmp != 0) {
            return cmp;
        }
        return Optional.ofNullable(a).map(ContainerDTO::getId).orElse("").compareToIgnoreCase(Optional.ofNullable(b).map(ContainerDTO::getId).orElse(""));
    }

    private Map<String, Object> buildContainerSpec(ContainerDTO container) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("name", Optional.ofNullable(trimName(container.getName())).filter(name -> !name.isBlank()).orElse(container.getId()));
        String image = resolveAssetImage(container);
        if (image != null && !image.isBlank()) {
            spec.put("image", image);
        }
        int port = container.getContainerPort() > 0 ? container.getContainerPort() : 80;
        List<Map<String, Object>> ports = new ArrayList<>();
        Map<String, Object> portEntry = new LinkedHashMap<>();
        portEntry.put("containerPort", port);
        ports.add(portEntry);
        spec.put("ports", ports);
        return spec;
    }

    private String resolveAssetImage(ContainerDTO node) {
        if (node == null || node.getAssetId() == null || node.getAssetId().isBlank()) {
            return "";
        }
        if (assetRepository == null) {
            return "";
        }
        try {
            return assetRepository.findById(node.getAssetId())
                    .map(Asset::getImage)
                    .orElse("");
        } catch (Exception ex) {
            log.warn("Unable to resolve asset {} for container {}: {}", node.getAssetId(), node.getName(), ex.getMessage());
            return "";
        }
    }

    private void validateUniqueContainerNames(DeploymentDTO deployment,
                                              List<Map<String, Object>> mainContainers,
                                              List<ContainerAttachment> initContainers,
                                              List<ContainerAttachment> sidecars) {
        Map<String, List<ContainerConflict>> conflictsByName = new LinkedHashMap<>();

        for (Map<String, Object> main : Optional.ofNullable(mainContainers).orElse(List.of())) {
            String name = trimName(getString(main, "name", null));
            if (name != null && !name.isBlank()) {
                conflictsByName.computeIfAbsent(name, key -> new ArrayList<>())
                        .add(new ContainerConflict("main", deployment.getId(), null));
            }
        }

        for (ContainerAttachment att : Optional.ofNullable(initContainers).orElse(List.of())) {
            addAttachmentConflict(conflictsByName, att, "init");
        }
        for (ContainerAttachment att : Optional.ofNullable(sidecars).orElse(List.of())) {
            addAttachmentConflict(conflictsByName, att, "sidecar");
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
                        .map(conflict -> conflict.type +
                                "(node:" + conflict.nodeId +
                                (conflict.linkId != null ? ", link:" + conflict.linkId : "") + ")")
                        .collect(Collectors.joining(", ")))
                .collect(Collectors.joining("; "));

        throw new IllegalArgumentException(
                "Workload " + deployment.getName() + " (" + deployment.getId() + ") has duplicate container name(s): "
                        + String.join(", ", duplicates) + ". Conflicts: " + details
        );
    }

    private void addAttachmentConflict(Map<String, List<ContainerConflict>> conflictsByName,
                                       ContainerAttachment attachment,
                                       String type) {
        if (attachment == null || attachment.container == null) {
            return;
        }
        String name = Optional.ofNullable(trimName(attachment.container.getName())).filter(v -> !v.isBlank()).orElse(null);
        if (name == null) {
            return;
        }
        String linkId = null;
        if (attachment.link != null) {
            linkId = attachment.link.getId();
            if (linkId == null || linkId.isBlank()) {
                linkId = attachment.link.getSource() + "->" + attachment.link.getTarget();
            }
        }
        conflictsByName.computeIfAbsent(name, key -> new ArrayList<>())
                .add(new ContainerConflict(type, attachment.container.getId(), linkId));
    }

    private static class ContainerAttachment {
        private final ContainerDTO container;
        private final ContainerRole role;
        private final LinkDTO link;

        private ContainerAttachment(ContainerDTO container, ContainerRole role, LinkDTO link) {
            this.container = container;
            this.role = role;
            this.link = link;
        }
    }

    private static class ContainerConflict {
        private final String type;
        private final String nodeId;
        private final String linkId;

        private ContainerConflict(String type, String nodeId, String linkId) {
            this.type = type;
            this.nodeId = nodeId;
            this.linkId = linkId;
        }
    }

    private void applyLinkedConfigReferences(DeploymentDTO node,
                                             Map<String, Object> templateSpec,
                                             Map<String, List<NodeDTO>> targetsBySource,
                                             Map<String, List<NodeDTO>> sourcesByTarget) {
        List<ConfigMapDTO> linkedConfigs = collectLinkedConfigNodes(node, targetsBySource, sourcesByTarget);
        if (linkedConfigs.isEmpty()) {
            return;
        }

        List<Map<String, Object>> containers = this.<Map<String, Object>>getList(templateSpec, "containers");
        if (containers == null || containers.isEmpty()) {
            return;
        }

        for (Map<String, Object> container : containers) {
            ensureEnvFromEntries(container, linkedConfigs);
        }

        List<Map<String, Object>> initContainers = this.<Map<String, Object>>getList(templateSpec, "initContainers");
        if (initContainers != null && !initContainers.isEmpty()) {
            for (Map<String, Object> initContainer : initContainers) {
                ensureEnvFromEntries(initContainer, linkedConfigs);
            }
        }
    }

    private List<ConfigMapDTO> collectLinkedConfigNodes(DeploymentDTO node,
                                                        Map<String, List<NodeDTO>> targetsBySource,
                                                        Map<String, List<NodeDTO>> sourcesByTarget) {
        if (node == null || node.getId() == null) {
            return List.of();
        }

        Stream<NodeDTO> outgoing = Optional.ofNullable(targetsBySource.get(node.getId()))
                .orElse(List.of())
                .stream();
        Stream<NodeDTO> incoming = Optional.ofNullable(sourcesByTarget.get(node.getId()))
                .orElse(List.of())
                .stream();

        return Stream.concat(outgoing, incoming)
                .filter(Objects::nonNull)
                .filter(ConfigMapDTO.class::isInstance)
                .map(ConfigMapDTO.class::cast)
                .filter(cfg -> cfg.getName() != null && !cfg.getName().isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(ConfigMapDTO::getId, cfg -> cfg, (existing, ignored) -> existing, LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())
                ));
    }

    private void ensureEnvFromEntries(Map<String, Object> container, List<ConfigMapDTO> linkedConfigs) {
        if (container == null || linkedConfigs == null || linkedConfigs.isEmpty()) {
            return;
        }

        List<Map<String, Object>> envFrom = this.<Map<String, Object>>getList(container, "envFrom");
        if (envFrom == null) {
            envFrom = new ArrayList<>();
            container.put("envFrom", envFrom);
        }

        Set<String> existingKeys = envFrom.stream()
                .map(this::resolveEnvFromKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (ConfigMapDTO config : linkedConfigs) {
            String name = config.getName();
            if (name == null || name.isBlank()) {
                continue;
            }

            BundleSensitivity sensitivity = detectBundleSensitivity(config);

            if (sensitivity.hasPlainEntries()) {
                String key = "configmap:" + name;
                if (!existingKeys.contains(key)) {
                    envFrom.add(buildEnvFromEntry(name, false));
                    existingKeys.add(key);
                }
            }

            if (sensitivity.hasSecretEntries()) {
                String key = "secret:" + name;
                if (!existingKeys.contains(key)) {
                    envFrom.add(buildEnvFromEntry(name, true));
                    existingKeys.add(key);
                }
            }

            if (!sensitivity.hasPlainEntries() && !sensitivity.hasSecretEntries()) {
                String key = "configmap:" + name;
                if (!existingKeys.contains(key)) {
                    envFrom.add(buildEnvFromEntry(name, false));
                    existingKeys.add(key);
                }
            }
        }
    }

    private String resolveEnvFromKey(Map<String, Object> envFromEntry) {
        if (envFromEntry == null) {
            return null;
        }

        Map<String, Object> configMapRef = getMap(envFromEntry, "configMapRef");
        if (configMapRef != null) {
            String name = getString(configMapRef, "name", null);
            return name == null ? null : "configmap:" + name;
        }

        Map<String, Object> secretRef = getMap(envFromEntry, "secretRef");
        if (secretRef != null) {
            String name = getString(secretRef, "name", null);
            return name == null ? null : "secret:" + name;
        }

        return null;
    }

    private Map<String, Object> buildEnvFromEntry(String name, boolean secret) {
        Map<String, Object> entry = new LinkedHashMap<>();
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("name", name);
        entry.put(secret ? "secretRef" : "configMapRef", ref);
        return entry;
    }

    private void updateServiceManifest(ServiceDTO node, Map<String, ManifestEntry> manifests, Map<String, String> statefulServiceSelectors) {
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
        metadata.putIfAbsent("namespace", resolveNamespace(node));
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
        String desiredSelector = statefulServiceSelectors.get(node.getId());
        if (desiredSelector != null && !desiredSelector.isBlank()) {
            Map<String, Object> selector = Optional.ofNullable(getMap(spec, "selector")).orElseGet(LinkedHashMap::new);
            selector.put("app", desiredSelector);
            spec.put("selector", selector);
            spec.put("clusterIP", "None");
        }
        spec.put("ports", ports);
        manifest.put("spec", spec);
    }

    private void updateIngressLikeManifest(IngressDTO node, Map<String, ManifestEntry> manifests) {
        manifests.entrySet().removeIf(entry ->
                entry.getKey().equals(node.getId()) || entry.getKey().startsWith(node.getId() + ":"));

        String gatewayName = trimName(node.getName()) + "-gateway";
        updateGatewayManifestEntry(node.getId() + ":gateway", gatewayName, resolveNamespace(node), node.getHost(), node.getTls(), manifests);

        VirtualServiceDTO virtualService = new VirtualServiceDTO(
                node.getId(),
                node.getName(),
                node.getHost(),
                node.getPath(),
                node.getServiceName(),
                node.getServicePort()
        );
        updateVirtualServiceManifestEntry(virtualService, manifests);
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
        metadata.putIfAbsent("namespace", resolveNamespace(node));
        manifest.put("metadata", metadata);

        updateVirtualServiceManifest(manifest, node);

        String gatewayName = trimName(node.getName()) + "-gateway";
        updateGatewayManifestEntry(node.getId() + ":gateway", gatewayName, resolveNamespace(node), node.getHost(), null, manifests);
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

        String gatewayName = trimName(node.getName()) + "-gateway";
        List<String> gateways = Optional.ofNullable(this.<String>getList(spec, "gateways"))
                .orElseGet(() -> new ArrayList<>());
        if (gateways.isEmpty()) {
            gateways.add(gatewayName);
        } else {
            gateways.set(0, gatewayName);
        }
        spec.put("gateways", gateways);

        List<Map<String, Object>> http = Optional.ofNullable(this.<Map<String, Object>>getList(spec, "http"))
                .orElseGet(() -> new ArrayList<>());
        Map<String, Object> httpEntry = http.isEmpty() ? new LinkedHashMap<>() : http.get(0);
        List<Map<String, Object>> matches = Optional.ofNullable(this.<Map<String, Object>>getList(httpEntry, "match"))
                .orElseGet(() -> new ArrayList<>());
        Map<String, Object> match = matches.isEmpty() ? new LinkedHashMap<>() : matches.get(0);
        String path = node.getPath() != null && !node.getPath().isBlank() ? node.getPath() : "/";
        match.put("uri", Map.of("prefix", path));
        if (matches.isEmpty()) {
            matches.add(match);
        }
        httpEntry.put("match", matches);

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
        DeploymentWorkloadType workloadType = node.resolveWorkloadType();
        String sanitizedName = trimName(node.getName());
        String kind = switch (workloadType) {
            case STATEFULSET -> "StatefulSet";
            case DAEMONSET -> "DaemonSet";
            default -> "Deployment";
        };
        manifest.put("kind", kind);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", sanitizedName);
        metadata.put("namespace", resolveNamespace(node));
        manifest.put("metadata", metadata);

        Map<String, Object> spec = new LinkedHashMap<>();
        if (workloadType != DeploymentWorkloadType.DAEMONSET) {
            spec.put("replicas", node.getReplicas());
        }
        Map<String, Object> selector = new LinkedHashMap<>();
        selector.put("matchLabels", Map.of("app", sanitizedName));
        spec.put("selector", selector);
        if (workloadType == DeploymentWorkloadType.STATEFULSET) {
            spec.put("serviceName", sanitizedName);
        }
        Map<String, Object> template = new LinkedHashMap<>();
        Map<String, Object> tempMeta = new LinkedHashMap<>();
        tempMeta.put("labels", Map.of("app", sanitizedName));
        template.put("metadata", tempMeta);
        Map<String, Object> tempSpec = new LinkedHashMap<>();
        tempSpec.put("containers", new ArrayList<Map<String, Object>>());
        template.put("spec", tempSpec);
        spec.put("template", template);
        manifest.put("spec", spec);
        applyPrimaryContainerSpec(node, tempSpec);
        return manifest;
    }

    private Map<String, Object> createBaseServiceManifest(ServiceDTO node) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", "v1");
        manifest.put("kind", "Service");
        String sanitizedName = trimName(node.getName());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", sanitizedName);
        metadata.put("namespace", resolveNamespace(node));
        manifest.put("metadata", metadata);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("type", node.getType());
        Map<String, Object> ports = new LinkedHashMap<>();
        ports.put("port", node.getPort());
        spec.put("ports", List.of(ports));
        Map<String, Object> selector = new LinkedHashMap<>();
        selector.put("app", sanitizedName);
        spec.put("selector", selector);
        manifest.put("spec", spec);
        return manifest;
    }

    private Map<String, Object> createBaseVirtualServiceManifest(VirtualServiceDTO node) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", "networking.istio.io/v1beta1");
        manifest.put("kind", "VirtualService");
        String sanitizedName = trimName(node.getName());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", sanitizedName);
        metadata.put("namespace", resolveNamespace(node));
        manifest.put("metadata", metadata);
        Map<String, Object> spec = new LinkedHashMap<>();
        String host = Optional.ofNullable(node.getHost()).orElse("example.com");
        spec.put("hosts", new ArrayList<>(List.of(host)));
        spec.put("gateways", new ArrayList<>());
        spec.put("http", new ArrayList<>());
        manifest.put("spec", spec);
        return manifest;
    }

    private void updateGatewayManifestEntry(String entryKey,
                                            String gatewayName,
                                            String namespace,
                                            String host,
                                            String tlsSecret,
                                            Map<String, ManifestEntry> manifests) {
        ManifestEntry entry = manifests.get(entryKey);
        if (entry == null || !"gateway".equals(entry.kind)) {
            entry = new ManifestEntry("gateway", gatewayName, createBaseGatewayManifest(gatewayName, namespace));
            manifests.put(entryKey, entry);
        }
        Map<String, Object> manifest = entry.manifest;
        manifest.put("apiVersion", "networking.istio.io/v1beta1");
        manifest.put("kind", "Gateway");

        Map<String, Object> metadata = Optional.ofNullable(getMap(manifest, "metadata")).orElseGet(LinkedHashMap::new);
        metadata.put("name", gatewayName);
        metadata.putIfAbsent("namespace", namespace);
        manifest.put("metadata", metadata);

        updateGatewaySpec(manifest, host, tlsSecret);
    }

    private Map<String, Object> createBaseGatewayManifest(String name, String namespace) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", "networking.istio.io/v1beta1");
        manifest.put("kind", "Gateway");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", name);
        metadata.put("namespace", namespace);
        manifest.put("metadata", metadata);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("selector", Map.of("istio", "ingressgateway"));
        spec.put("servers", new ArrayList<>());
        manifest.put("spec", spec);
        return manifest;
    }

    private void updateGatewaySpec(Map<String, Object> manifest, String host, String tlsSecret) {
        Map<String, Object> spec = Optional.ofNullable(getMap(manifest, "spec")).orElseGet(LinkedHashMap::new);
        spec.put("selector", Map.of("istio", "ingressgateway"));
        List<Map<String, Object>> servers = new ArrayList<>();

        Map<String, Object> httpServer = new LinkedHashMap<>();
        httpServer.put("port", Map.of("number", 80, "name", "http", "protocol", "HTTP"));
        httpServer.put("hosts", List.of(StringUtils.hasText(host) ? host : "*"));
        servers.add(httpServer);

        if (StringUtils.hasText(tlsSecret)) {
            Map<String, Object> httpsServer = new LinkedHashMap<>();
            httpsServer.put("port", Map.of("number", 443, "name", "https", "protocol", "HTTPS"));
            httpsServer.put("hosts", List.of(StringUtils.hasText(host) ? host : "*"));
            httpsServer.put("tls", Map.of("mode", "SIMPLE", "credentialName", tlsSecret.trim()));
            servers.add(httpsServer);
        }

        spec.put("servers", servers);
        manifest.put("spec", spec);
    }


    private String resolveNamespace(ClusterDTO cluster) {
        return cluster == null ? "default" : resolveNamespace(cluster.getNameSpace());
    }

    private String resolveNamespace(NodeDTO node) {
        return node == null ? "default" : resolveNamespace(node.getNamespace());
    }

    private String resolveNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return "default";
        }
        return namespace.trim().toLowerCase(Locale.ROOT);
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
            case "gateway" -> applyGatewayHelmValues(manifest, entry.getName(), context);
            case "virtualservice", "istio", "ingress" -> applyVirtualServiceHelmValues(manifest, entry.getName(), context);
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

    private void applyGatewayHelmValues(Map<String, Object> manifest, String fallbackName, HelmValuesContext context) {
        templateResourceName(manifest, fallbackName, context);
        Map<String, Object> spec = ensureChildMap(manifest, "spec");
        List<Map<String, Object>> servers = this.<Map<String, Object>>getList(spec, "servers");
        if (servers == null || servers.isEmpty()) {
            return;
        }
        Map<String, Object> server = servers.get(0);
        List<String> hosts = this.<String>getList(server, "hosts");
        if (hosts != null && !hosts.isEmpty()) {
            String host = hosts.get(0);
            if (host != null && !host.isBlank()) {
                context.values.put("host", host);
                hosts.set(0, context.valueRef("host"));
            }
        }
        Map<String, Object> tls = getMap(server, "tls");
        if (tls != null) {
            String secret = getString(tls, "credentialName", null);
            if (secret != null && !secret.isBlank()) {
                context.values.put("tlsSecretName", secret);
                tls.put("credentialName", context.valueRef("tlsSecretName"));
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
        List<String> gateways = this.<String>getList(spec, "gateways");
        if (gateways != null && !gateways.isEmpty()) {
            String gateway = gateways.get(0);
            if (gateway != null && !gateway.isBlank()) {
                context.values.put("gatewayName", gateway);
                gateways.set(0, context.valueRef("gatewayName"));
            }
        }
        List<Map<String, Object>> http = this.<Map<String, Object>>getList(spec, "http");
        if (http == null || http.isEmpty()) {
            return;
        }
        Map<String, Object> httpEntry = http.get(0);
        List<Map<String, Object>> matches = this.<Map<String, Object>>getList(httpEntry, "match");
        if (matches != null && !matches.isEmpty()) {
            Map<String, Object> match = matches.get(0);
            Map<String, Object> uri = ensureChildMap(match, "uri");
            String prefix = getString(uri, "prefix", null);
            if (prefix != null && !prefix.isBlank()) {
                context.values.put("pathPrefix", prefix);
                uri.put("prefix", context.valueRef("pathPrefix"));
            }
        }
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
            case "gateway", "ingress", "virtualservice", "istio" -> "routes";
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

    private Map<String, List<NodeDTO>> buildSourcesByTarget(Map<String, NodeDTO> nodesById, List<LinkDTO> links) {
        Map<String, List<NodeDTO>> sourcesByTarget = new HashMap<>();
        if (links == null || links.isEmpty() || nodesById.isEmpty()) {
            return sourcesByTarget;
        }
        for (LinkDTO link : links) {
            NodeDTO source = nodesById.get(link.getSource());
            if (source == null) {
                continue;
            }
            sourcesByTarget.computeIfAbsent(link.getTarget(), key -> new ArrayList<>()).add(source);
        }
        return sourcesByTarget;
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
