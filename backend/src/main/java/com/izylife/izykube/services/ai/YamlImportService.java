package com.izylife.izykube.services.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.IngressDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.PodDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class YamlImportService {

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
    private final Yaml yamlParser;
    private final Yaml yamlDumper;

    public YamlImportService() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        this.yamlParser = new Yaml();
        this.yamlDumper = new Yaml(options);
    }

    public ClusterDTO importCluster(String yamlContent, String clusterName) {
        if (yamlContent == null || yamlContent.isBlank()) {
            throw new IllegalArgumentException("YAML content is empty");
        }

        Iterable<Object> documents = yamlParser.loadAll(yamlContent);
        List<NodeDTO> nodes = new ArrayList<>();

        for (Object document : documents) {
            if (!(document instanceof Map)) {
                continue;
            }

            Map<String, Object> resource = (Map<String, Object>) document;
            String kind = getString(resource, "kind");
            if (kind == null) {
                continue;
            }

            kind = kind.toLowerCase(Locale.ROOT);
            Map<String, Object> metadata = getMap(resource, "metadata");
            String name = metadata != null ? getString(metadata, "name") : null;
            if (name == null || name.isBlank()) {
                name = kind + "-" + UUID.randomUUID().toString().substring(0, 6);
            }

            String id = UUID.randomUUID().toString();

            try {
                switch (kind) {
                    case "configmap" -> nodes.add(buildConfigMapNode(id, name, resource));
                    case "deployment" -> nodes.add(buildDeploymentNode(id, name, resource));
                    case "service" -> nodes.add(buildServiceNode(id, name, resource));
                    case "pod" -> nodes.add(buildPodNode(id, name, resource));
                    case "ingress" -> nodes.add(buildIngressNode(id, name, resource));
                    default -> log.info("Skipping unsupported kind '{}' in YAML import", kind);
                }
            } catch (Exception e) {
                log.warn("Failed to process resource '{}' of kind '{}': {}", name, kind, e.getMessage());
            }
        }

        ClusterDTO clusterDTO = new ClusterDTO();
        clusterDTO.setName(clusterName != null && !clusterName.isBlank() ? clusterName : "AI Generated Cluster");
        clusterDTO.setNodes(nodes);
        clusterDTO.setLinks(new ArrayList<>());
        clusterDTO.setDiagram(buildDiagram(nodes));
        return clusterDTO;
    }

    private String buildDiagram(List<NodeDTO> nodes) {
        List<Map<String, Object>> diagramNodes = new ArrayList<>();
        List<Map<String, Object>> diagramLinks = new ArrayList<>();

        int spacingX = 180;
        int spacingY = 150;
        int startX = 120;
        int startY = 120;

        for (int index = 0; index < nodes.size(); index++) {
            NodeDTO node = nodes.get(index);
            int column = index % 3;
            int row = index / 3;

            Map<String, Object> diagramNode = new HashMap<>();
            diagramNode.put("id", node.getId());
            diagramNode.put("name", node.getName());
            diagramNode.put("type", node.getKind());
            diagramNode.put("icon", ICON_MAP.getOrDefault(node.getKind(), "assets/images/diagram/container.svg"));
            diagramNode.put("x", startX + column * spacingX);
            diagramNode.put("y", startY + row * spacingY);
            diagramNodes.add(diagramNode);
        }

        Map<String, Object> diagram = new HashMap<>();
        diagram.put("nodes", diagramNodes);
        diagram.put("links", diagramLinks);

        try {
            return objectMapper.writeValueAsString(diagram);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build diagram representation", e);
        }
    }

    private ConfigMapDTO buildConfigMapNode(String id, String name, Map<String, Object> resource) {
        String yaml = yamlDumper.dump(resource);
        return new ConfigMapDTO(id, name, yaml);
    }

    private DeploymentDTO buildDeploymentNode(String id, String name, Map<String, Object> resource) {
        Map<String, Object> spec = getMap(resource, "spec");
        int replicas = spec != null && spec.get("replicas") instanceof Number
                ? ((Number) spec.get("replicas")).intValue()
                : 1;
        String strategyType = "RollingUpdate";
        if (spec != null) {
            Map<String, Object> strategy = getMap(spec, "strategy");
            if (strategy != null) {
                String type = getString(strategy, "type");
                if (type != null) {
                    strategyType = type;
                }
            }
        }
        return new DeploymentDTO(id, name, replicas, strategyType);
    }

    private ServiceDTO buildServiceNode(String id, String name, Map<String, Object> resource) {
        Map<String, Object> spec = getMap(resource, "spec");
        String type = spec != null ? getString(spec, "type") : "ClusterIP";
        int port = 80;
        Integer nodePort = null;

        if (spec != null) {
            List<Map<String, Object>> ports = getList(spec, "ports");
            if (ports != null && !ports.isEmpty()) {
                Map<String, Object> firstPort = ports.get(0);
                if (firstPort.get("port") instanceof Number) {
                    port = ((Number) firstPort.get("port")).intValue();
                }
                if (firstPort.get("nodePort") instanceof Number) {
                    nodePort = ((Number) firstPort.get("nodePort")).intValue();
                }
            }
        }

        return new ServiceDTO(id, name, type != null ? type : "ClusterIP", port, nodePort, false, null);
    }

    private PodDTO buildPodNode(String id, String name, Map<String, Object> resource) {
        Map<String, Object> spec = getMap(resource, "spec");
        String restartPolicy = spec != null ? getString(spec, "restartPolicy") : "Always";
        String serviceAccount = spec != null ? getString(spec, "serviceAccountName") : null;
        Map<String, String> nodeSelector = null;
        if (spec != null && spec.get("nodeSelector") instanceof Map selectorMap) {
            nodeSelector = (Map<String, String>) selectorMap;
        }
        Boolean hostNetwork = spec != null && spec.get("hostNetwork") instanceof Boolean
                ? (Boolean) spec.get("hostNetwork")
                : null;
        String dnsPolicy = spec != null ? getString(spec, "dnsPolicy") : null;
        String schedulerName = spec != null ? getString(spec, "schedulerName") : null;
        Integer priority = spec != null && spec.get("priority") instanceof Number
                ? ((Number) spec.get("priority")).intValue()
                : null;
        String preemptionPolicy = spec != null ? getString(spec, "preemptionPolicy") : null;

        return new PodDTO(
                id,
                name,
                restartPolicy != null ? restartPolicy : "Always",
                serviceAccount,
                nodeSelector,
                hostNetwork,
                dnsPolicy,
                schedulerName,
                priority,
                preemptionPolicy
        );
    }

    private IngressDTO buildIngressNode(String id, String name, Map<String, Object> resource) {
        Map<String, Object> spec = getMap(resource, "spec");
        String host = "example.com";
        String path = "/";
        String serviceName = "";
        int servicePort = 80;

        if (spec != null) {
            List<Map<String, Object>> rules = getList(spec, "rules");
            if (rules != null && !rules.isEmpty()) {
                Map<String, Object> rule = rules.get(0);
                host = getString(rule, "host") != null ? getString(rule, "host") : host;
                Map<String, Object> http = getMap(rule, "http");
                if (http != null) {
                    List<Map<String, Object>> paths = getList(http, "paths");
                    if (paths != null && !paths.isEmpty()) {
                        Map<String, Object> firstPath = paths.get(0);
                        path = getString(firstPath, "path") != null ? getString(firstPath, "path") : path;
                        Map<String, Object> backend = getMap(firstPath, "backend");
                        if (backend != null) {
                            Map<String, Object> service = getMap(backend, "service");
                            if (service != null) {
                                serviceName = getString(service, "name") != null ? getString(service, "name") : serviceName;
                                Map<String, Object> port = getMap(service, "port");
                                if (port != null) {
                                    if (port.get("number") instanceof Number) {
                                        servicePort = ((Number) port.get("number")).intValue();
                                    }
                                }
                            } else {
                                String legacyServiceName = getString(backend, "serviceName");
                                if (legacyServiceName != null) {
                                    serviceName = legacyServiceName;
                                }
                                Map<String, Object> legacyServicePort = getMap(backend, "servicePort");
                                if (legacyServicePort != null && legacyServicePort.get("number") instanceof Number) {
                                    servicePort = ((Number) legacyServicePort.get("number")).intValue();
                                } else if (backend.get("servicePort") instanceof Number) {
                                    servicePort = ((Number) backend.get("servicePort")).intValue();
                                }
                            }
                        }
                    }
                }
            }
        }

        return new IngressDTO(id, name, host, path, serviceName, servicePort);
    }

    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private String getString(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        return value != null ? value.toString() : null;
    }

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
}
