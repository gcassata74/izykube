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

package com.izylife.izykube.services.ai;

import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.ConfigEntryDTO;
import com.izylife.izykube.dto.cluster.ConfigEntrySensitivity;
import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.DeploymentWorkloadType;
import com.izylife.izykube.dto.cluster.AccessPolicyDTO;
import com.izylife.izykube.dto.cluster.AccessPolicyRuleDTO;
import com.izylife.izykube.dto.cluster.IngressDTO;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.SecretDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import com.izylife.izykube.dto.cluster.ServiceAccountDTO;
import com.izylife.izykube.dto.cluster.VirtualServiceDTO;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClusterYamlServiceTest {

    private final ClusterYamlService service = new ClusterYamlService();

    @Test
    void importClusterAcceptsIndentedYaml() {
        String yaml = """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: demo-config
                """;
        String indentedYaml = yaml.lines()
                .map(line -> line.isEmpty() ? line : "    " + line)
                .reduce((a, b) -> a + "\n" + b)
                .orElseThrow();

        ClusterDTO cluster = service.importCluster(indentedYaml, null);

        assertFalse(cluster.getNodes().isEmpty(), "Expected at least one node to be imported");
        assertEquals("demo-config", cluster.getNodes().get(0).getName());
    }

    @Test
    void importClusterRejectsMalformedYaml() {
        String invalidYaml = """
                apiVersion: v1
                kind ConfigMap
                """;

        ClusterYamlException exception = assertThrows(ClusterYamlException.class,
                () -> service.importCluster(invalidYaml, null));

        assertTrue(exception.getMessage().startsWith("Invalid YAML content"));
    }

    @Test
    void importClusterDecodesSecretData() {
        String yaml = """
                apiVersion: v1
                kind: Secret
                metadata:
                  name: demo-secret
                data:
                  username: YWRtaW4=
                  password: cGFzc3dvcmQ=
                """;

        ClusterDTO cluster = service.importCluster(yaml, null);

        assertFalse(cluster.getNodes().isEmpty());
        assertTrue(cluster.getNodes().get(0) instanceof SecretDTO);
        SecretDTO secret = (SecretDTO) cluster.getNodes().get(0);
        Map<String, Object> values = new Yaml().load(secret.getYaml());
        assertEquals("admin", values.get("username"));
        assertEquals("password", values.get("password"));
    }

    @Test
    void exportHelmChartProducesZipWithTemplates() throws Exception {
        ClusterDTO cluster = ClusterDTO.builder()
                .name("Demo Cluster")
                .diagram("""
                        {
                          "rawManifests": [
                            {
                              "kind": "deployment",
                              "name": "web",
                              "manifest": {
                                "apiVersion": "apps/v1",
                                "kind": "Deployment",
                                "metadata": { "name": "web" },
                                "spec": {
                                  "replicas": 2,
                                  "template": {
                                    "spec": {
                                      "containers": [
                                        { "name": "web", "image": "nginx:1.25" }
                                      ]
                                    }
                                  }
                                }
                              }
                            }
                          ]
                        }
                        """)
                .build();
        cluster.getNodes().add(new DeploymentDTO("web", "web", 2, "RollingUpdate", "", 80));

        HelmChartArchive archive = service.exportHelmChart(cluster);

        assertNotNull(archive);
        assertTrue(archive.fileName().endsWith(".zip"));

        List<String> entryNames = new ArrayList<>();
        String chartYaml = null;
        String valuesYaml = null;
        String templateYaml = null;

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(archive.content()), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                String content = readZipEntry(zin);
                if (entry.getName().endsWith("Chart.yaml")) {
                    chartYaml = content;
                } else if (entry.getName().endsWith("values.yaml")) {
                    valuesYaml = content;
                } else if (entry.getName().contains("templates/")) {
                    templateYaml = content;
                }
            }
        }

        assertNotNull(chartYaml);
        assertNotNull(valuesYaml);
        assertNotNull(templateYaml);

        assertTrue(entryNames.stream().anyMatch(name -> name.endsWith("/Chart.yaml")));
        assertTrue(entryNames.stream().anyMatch(name -> name.endsWith("/values.yaml")));
        assertTrue(chartYaml.contains("name: demo-cluster"));

        Map<String, Object> values = new Yaml().load(valuesYaml);
        Map<String, Object> deployments = castMap(values.get("deployments"));
        Map<String, Object> deployment = castMap(deployments.get("web"));
        assertEquals(2, ((Number) deployment.get("replicas")).intValue());
        Map<String, Object> containers = castMap(deployment.get("containers"));
        Map<String, Object> mainContainer = castMap(containers.get("web"));
        assertEquals("nginx:1.25", mainContainer.get("image"));

        assertTrue(templateYaml.contains("{{ .Values.deployments.web.replicas }}"));
        assertTrue(templateYaml.contains("{{ .Values.deployments.web.containers.web.image }}"));
    }

    @Test
    void exportClusterEncodesSecretData() {
        ClusterDTO cluster = ClusterDTO.builder()
                .diagram("{}")
                .build();
        cluster.getNodes().add(new SecretDTO("db-secret", "db-secret", "password: super-secret"));

        String exported = service.exportCluster(cluster);
        System.out.println(exported);

        assertTrue(exported.contains("kind: Secret"));
        assertTrue(exported.contains("c3VwZXItc2VjcmV0"));
    }

    @Test
    void exportClusterRejectsInvalidServiceAccountName() {
        ClusterDTO cluster = ClusterDTO.builder().diagram("{}").build();
        ServiceAccountDTO sa = new ServiceAccountDTO("sa-1", "Example-SA");
        sa.setNamespace("test-ns");
        cluster.getNodes().add(sa);

        assertThrows(IllegalArgumentException.class, () -> service.exportCluster(cluster));
    }

    @Test
    void exportClusterGeneratesRbacForAccessPolicyWorkload() {
        ClusterDTO cluster = ClusterDTO.builder()
                .diagram("{}")
                .nameSpace("demo")
                .build();

        DeploymentDTO deployment = new DeploymentDTO("dep-1", "my-app", 1, "RollingUpdate", "", 80);
        cluster.getNodes().add(deployment);

        AccessPolicyDTO policy = new AccessPolicyDTO("ap-1", "my-app-reader");
        policy.setNamespace("demo");
        AccessPolicyRuleDTO rule = new AccessPolicyRuleDTO();
        rule.setApiGroups(List.of(""));
        rule.setResources(List.of("pods"));
        rule.setVerbs(List.of("get", "list"));
        policy.setRules(List.of(rule));
        cluster.getNodes().add(policy);

        LinkDTO link = new LinkDTO();
        link.setSource(policy.getId());
        link.setTarget(deployment.getId());
        link.setType("appliesTo");
        cluster.getLinks().add(link);

        String exported = service.exportCluster(cluster);

        Map<String, Object> serviceAccountDoc = null;
        Map<String, Object> roleDoc = null;
        Map<String, Object> roleBindingDoc = null;
        Map<String, Object> deploymentDoc = null;
        for (Object doc : new Yaml().loadAll(exported)) {
            if (!(doc instanceof Map<?, ?> map)) {
                continue;
            }
            if ("ServiceAccount".equals(map.get("kind"))) {
                serviceAccountDoc = castMap(doc);
            } else if ("Role".equals(map.get("kind"))) {
                roleDoc = castMap(doc);
            } else if ("RoleBinding".equals(map.get("kind"))) {
                roleBindingDoc = castMap(doc);
            } else if ("Deployment".equals(map.get("kind"))) {
                deploymentDoc = castMap(doc);
            }
        }

        assertNotNull(serviceAccountDoc);
        assertNotNull(roleDoc);
        assertNotNull(roleBindingDoc);
        Map<String, Object> roleRef = castMap(roleBindingDoc.get("roleRef"));
        assertEquals("Role", roleRef.get("kind"));
        assertEquals("my-app-reader", roleRef.get("name"));
        List<Map<String, Object>> subjects = (List<Map<String, Object>>) roleBindingDoc.get("subjects");
        assertNotNull(subjects);
        assertEquals("ServiceAccount", subjects.get(0).get("kind"));
        assertEquals("my-app-sa", subjects.get(0).get("name"));
        assertEquals("demo", subjects.get(0).get("namespace"));

        assertNotNull(deploymentDoc);
        Map<String, Object> spec = castMap(deploymentDoc.get("spec"));
        Map<String, Object> template = castMap(spec.get("template"));
        Map<String, Object> podSpec = castMap(template.get("spec"));
        assertEquals("my-app-sa", podSpec.get("serviceAccountName"));
    }

    @Test
    void exportClusterSplitsConfigBundleIntoConfigMapAndSecret() {
        ConfigMapDTO bundle = new ConfigMapDTO("config-bundle-a", "config-bundle-a", null);

        ConfigEntryDTO plainEntry = new ConfigEntryDTO();
        plainEntry.setKey("MYSQL_HOST");
        plainEntry.setValue("mysql-service");
        plainEntry.setSensitivity(ConfigEntrySensitivity.PLAIN);

        ConfigEntryDTO secretEntry = new ConfigEntryDTO();
        secretEntry.setKey("MYSQL_ROOT_PASSWORD");
        secretEntry.setValue("admin");
        secretEntry.setSensitivity(ConfigEntrySensitivity.SECRET);

        bundle.setEntries(List.of(plainEntry, secretEntry));

        ClusterDTO cluster = ClusterDTO.builder()
                .diagram("{}")
                .build();
        cluster.getNodes().add(bundle);

        String exported = service.exportCluster(cluster);

        assertTrue(exported.contains("kind: ConfigMap"));
        assertTrue(exported.contains("kind: Secret"));
        assertTrue(exported.contains("name: config-bundle-a"));
        assertTrue(exported.contains("MYSQL_HOST: mysql-service"));
        assertTrue(exported.contains("YWRtaW4=")); // admin encoded
    }

    @Test
    void exportClusterDoesNotDuplicateKeysBetweenConfigMapAndSecret() {
        ConfigMapDTO bundle = new ConfigMapDTO("config-bundle-b", "config-bundle-b", null);

        bundle.setEntries(List.of(
                entry("DB_USER", "root", ConfigEntrySensitivity.PLAIN),
                entry("DB_NAME", "testdb", ConfigEntrySensitivity.PLAIN),
                entry("DB_HOST", "mysql-service", ConfigEntrySensitivity.PLAIN),
                entry("DB_PASSWORD", "admin", ConfigEntrySensitivity.SECRET)
        ));
        bundle.setNamespace("test-image");

        ClusterDTO cluster = ClusterDTO.builder().diagram("{}").build();
        cluster.getNodes().add(bundle);

        String exported = service.exportCluster(cluster);

        Map<String, Object> configMapDoc = null;
        Map<String, Object> secretDoc = null;
        for (Object doc : new Yaml().loadAll(exported)) {
            if (!(doc instanceof Map<?, ?> map)) {
                continue;
            }
            if ("ConfigMap".equals(map.get("kind"))) {
                configMapDoc = castMap(doc);
            } else if ("Secret".equals(map.get("kind"))) {
                secretDoc = castMap(doc);
            }
        }

        assertNotNull(configMapDoc, "Expected ConfigMap document");
        assertNotNull(secretDoc, "Expected Secret document");

        Map<String, Object> configData = castMap(configMapDoc.get("data"));
        assertEquals(3, configData.size());
        assertEquals("root", configData.get("DB_USER"));
        assertEquals("testdb", configData.get("DB_NAME"));
        assertEquals("mysql-service", configData.get("DB_HOST"));
        assertFalse(configData.containsKey("DB_PASSWORD"), "Secret key must not appear in ConfigMap");

        Map<String, Object> secretData = castMap(secretDoc.get("data"));
        assertEquals(1, secretData.size());
        assertTrue(secretData.containsKey("DB_PASSWORD"));
        assertTrue(secretData.get("DB_PASSWORD").toString().startsWith("YWRtaW4"), "Secret must be encoded");
        assertFalse(secretData.containsKey("DB_USER"));
        assertFalse(secretData.containsKey("DB_NAME"));
        assertFalse(secretData.containsKey("DB_HOST"));
    }

    @Test
    void exportClusterAddsBothEnvFromEntriesForMixedBundles() {
        ConfigMapDTO bundle = new ConfigMapDTO("config-bundle-b", "config-bundle-b", null);
        bundle.setEntries(List.of(
                entry("DB_USER", "root", ConfigEntrySensitivity.PLAIN),
                entry("DB_PASSWORD", "admin", ConfigEntrySensitivity.SECRET)
        ));

        DeploymentDTO deployment = new DeploymentDTO("deployment:mysql", "deployment-a", 1, "RollingUpdate", "", 8080);

        LinkDTO link = new LinkDTO();
        link.setSource(bundle.getId());
        link.setTarget(deployment.getId());

        ClusterDTO cluster = ClusterDTO.builder()
                .diagram("{}")
                .build();
        cluster.setNodes(new ArrayList<>(List.of(bundle, deployment)));
        cluster.setLinks(List.of(link));

        String exported = service.exportCluster(cluster);

        Map<String, Object> deploymentManifest = null;
        for (Object doc : new Yaml().loadAll(exported)) {
            if (!(doc instanceof Map<?, ?> map)) {
                continue;
            }
            if ("Deployment".equals(map.get("kind"))) {
                deploymentManifest = castMap(doc);
                break;
            }
        }

        assertNotNull(deploymentManifest, "Expected Deployment manifest");
        Map<String, Object> spec = castMap(deploymentManifest.get("spec"));
        Map<String, Object> template = castMap(spec.get("template"));
        Map<String, Object> podSpec = castMap(template.get("spec"));
        var containers = (List<Map<String, Object>>) podSpec.get("containers");
        Map<String, Object> container = containers.get(0);
        List<Map<String, Object>> envFrom = (List<Map<String, Object>>) container.get("envFrom");

        Map<String, Object> configMapRef = envFrom.stream()
                .map(entry -> castMap(entry.get("configMapRef")))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        Map<String, Object> secretRef = envFrom.stream()
                .map(entry -> castMap(entry.get("secretRef")))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        assertNotNull(configMapRef, "Expected configMapRef in envFrom");
        assertNotNull(secretRef, "Expected secretRef in envFrom");
        assertEquals("config-bundle-b", configMapRef.get("name"));
        assertEquals("config-bundle-b", secretRef.get("name"));
    }

    private ConfigEntryDTO entry(String key, String value, ConfigEntrySensitivity sensitivity) {
        ConfigEntryDTO e = new ConfigEntryDTO();
        e.setKey(key);
        e.setValue(value);
        e.setSensitivity(sensitivity);
        return e;
    }

    @Test
    void exportClusterUsesStatefulSetWhenWorkloadTypeIsStatefulSet() {
        DeploymentDTO workload = new DeploymentDTO("deployment-1", "  my-db  ", 1, "RollingUpdate", "", 3306, DeploymentWorkloadType.STATEFULSET);

        ClusterDTO cluster = ClusterDTO.builder()
                .diagram("{}")
                .build();
        cluster.getNodes().add(workload);

        String exported = service.exportCluster(cluster);

        assertTrue(exported.contains("kind: StatefulSet"));
        assertTrue(exported.contains("name: my-db"));
        assertTrue(exported.contains("serviceName: my-db"));
    }

    @Test
    void importClusterCreatesDirectionalTrafficLinks() {
        String yaml = """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: app-config
                data:
                  mode: prod
                ---
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: web-app
                spec:
                  selector:
                    matchLabels:
                      app: web-app
                  template:
                    metadata:
                      labels:
                        app: web-app
                    spec:
                      containers:
                        - name: web
                          image: nginx
                          ports:
                            - containerPort: 8080
                ---
                apiVersion: v1
                kind: Service
                metadata:
                  name: web-app
                spec:
                  selector:
                    app: web-app
                  ports:
                    - port: 80
                      targetPort: 8080
                ---
                apiVersion: networking.k8s.io/v1
                kind: Ingress
                metadata:
                  name: web-app
                spec:
                  rules:
                    - host: example.com
                      http:
                        paths:
                          - path: /
                            pathType: Prefix
                            backend:
                              service:
                                name: web-app
                                port:
                                  number: 80
                """;

        ClusterDTO cluster = service.importCluster(yaml, null);
        List<LinkDTO> links = cluster.getLinks();

        assertTrue(links.stream().anyMatch(link ->
                "ingress:web-app".equals(link.getSource()) && "service:web-app".equals(link.getTarget())),
                "Expected ingress to point to service");
        assertTrue(links.stream().anyMatch(link ->
                "service:web-app".equals(link.getSource()) && "deployment:web-app".equals(link.getTarget())),
                "Expected service to link to deployment");
        assertTrue(cluster.getNodes().stream().noneMatch(node -> "pod".equalsIgnoreCase(node.getKind())),
                "Pod nodes should not be present in the imported cluster");
    }

    @Test
    void importClusterCapturesIngressMetadata() {
        String yaml = """
                apiVersion: networking.k8s.io/v1
                kind: Ingress
                metadata:
                  name: secure-app
                  annotations:
                    nginx.ingress.kubernetes.io/rewrite-target: /
                spec:
                  tls:
                    - hosts:
                        - secure.example.com
                      secretName: secure-secret
                  rules:
                    - host: secure.example.com
                      http:
                        paths:
                          - path: /
                            pathType: Prefix
                            backend:
                              service:
                                name: secure-service
                                port:
                                  number: 443
                """;

        ClusterDTO cluster = service.importCluster(yaml, null);

        IngressDTO ingress = cluster.getNodes().stream()
                .filter(node -> node instanceof IngressDTO)
                .map(IngressDTO.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals("secure-secret", ingress.getTls());
        assertEquals("secure-service", ingress.getServiceName());
        assertEquals(443, ingress.getServicePort());
        assertEquals("/", ingress.getPath());
        assertEquals("secure.example.com", ingress.getHost());
        assertEquals("/", ingress.getAnnotations().get("nginx.ingress.kubernetes.io/rewrite-target"));
    }

    @Test
    void importClusterKeepsVirtualServiceManifestWithoutCreatingVirtualServiceNode() {
        String yaml = """
                apiVersion: v1
                kind: Service
                metadata:
                  name: api
                spec:
                  selector:
                    app: api
                  ports:
                    - port: 8080
                      targetPort: 8080
                ---
                apiVersion: networking.istio.io/v1beta1
                kind: VirtualService
                metadata:
                  name: api-vs
                spec:
                  hosts:
                    - api.example.com
                  gateways:
                    - public-gateway
                  http:
                    - route:
                        - destination:
                            host: api
                            port:
                              number: 8080
                """;

        ClusterDTO cluster = service.importCluster(yaml, null);

        assertTrue(cluster.getNodes().stream().noneMatch(node ->
                        node instanceof VirtualServiceDTO || "istio".equalsIgnoreCase(node.getKind()) || "virtualservice".equalsIgnoreCase(node.getKind())),
                "VirtualService should not be materialized as a diagram node");
        assertTrue(cluster.getDiagram().contains("\"kind\":\"virtualservice\""),
                "VirtualService must still be preserved in raw manifests");
    }

    @Test
    void exportClusterResolvesIngressServiceFromLinks() {
        ClusterDTO cluster = ClusterDTO.builder()
                .diagram("""
                        {
                          "rawManifests": [
                            {
                              "kind": "service",
                              "name": "web-service",
                              "manifest": {
                                "apiVersion": "v1",
                                "kind": "Service",
                                "metadata": { "name": "web-service" },
                                "spec": { "ports": [{ "port": 80 }] }
                              }
                            },
                            {
                              "kind": "ingress",
                              "name": "web-ingress",
                              "manifest": {
                                "apiVersion": "networking.k8s.io/v1",
                                "kind": "Ingress",
                                "metadata": { "name": "web-ingress" }
                              }
                            }
                          ]
                        }
                        """)
                .build();

        ServiceDTO serviceNode = new ServiceDTO("service:web-service", "web-service", "ClusterIP", 80);
        IngressDTO ingressNode = new IngressDTO(
                "ingress:web-ingress",
                "web-ingress",
                "demo.example.com",
                "/",
                "",
                0,
                "demo-secret",
                Map.of("nginx.ingress.kubernetes.io/rewrite-target", "/")
        );
        cluster.getNodes().add(serviceNode);
        cluster.getNodes().add(ingressNode);

        LinkDTO link = new LinkDTO();
        link.setSource(ingressNode.getId());
        link.setTarget(serviceNode.getId());
        cluster.getLinks().add(link);

        assertFalse(ingressNode.getAnnotations().isEmpty());
        String exported = service.exportCluster(cluster);

        Yaml parser = new Yaml();
        Map<String, Object> ingressManifest = null;
        for (Object document : parser.loadAll(exported)) {
            Map<String, Object> manifest = castMap(document);
            if (!"Ingress".equals(manifest.get("kind"))) {
                continue;
            }
            Map<String, Object> specCandidate = castMap(manifest.get("spec"));
            if (specCandidate == null || specCandidate.get("rules") == null) {
                continue;
            }
            ingressManifest = manifest;
            break;
        }

        assertNotNull(ingressManifest, "Expected to find ingress manifest in export");
        Map<String, Object> metadata = castMap(ingressManifest.get("metadata"));
        Map<String, Object> annotations = castMap(metadata.get("annotations"));
        assertEquals("/", annotations.get("nginx.ingress.kubernetes.io/rewrite-target"));

        Map<String, Object> spec = castMap(ingressManifest.get("spec"));
        List<Map<String, Object>> rules = (List<Map<String, Object>>) spec.get("rules");
        Map<String, Object> rule = castMap(rules.get(0));
        Map<String, Object> http = castMap(rule.get("http"));
        List<Map<String, Object>> paths = (List<Map<String, Object>>) http.get("paths");
        Map<String, Object> backend = castMap(paths.get(0).get("backend"));
        Map<String, Object> service = castMap(backend.get("service"));
        assertEquals("web-service", service.get("name"));
        Map<String, Object> port = castMap(service.get("port"));
        assertEquals(80, ((Number) port.get("number")).intValue());

        List<Map<String, Object>> tls = (List<Map<String, Object>>) spec.get("tls");
        assertEquals("demo-secret", tls.get(0).get("secretName"));
    }

    @Test
    void exportClusterHandlesVirtualServiceNodes() {
        ClusterDTO cluster = ClusterDTO.builder()
                .diagram("""
                        {
                          "rawManifests": []
                        }
                        """)
                .build();

        ServiceDTO serviceNode = new ServiceDTO("service:api", "api", "ClusterIP", 8080);
        VirtualServiceDTO virtualServiceNode = new VirtualServiceDTO(
                "istio:api",
                "api-vs",
                "api.example.com",
                "/",
                "",
                0
        );
        cluster.getNodes().add(serviceNode);
        cluster.getNodes().add(virtualServiceNode);

        LinkDTO link = new LinkDTO();
        link.setSource(virtualServiceNode.getId());
        link.setTarget(serviceNode.getId());
        cluster.getLinks().add(link);

        String exported = service.exportCluster(cluster);
        Yaml parser = new Yaml();
        Map<String, Object> virtualService = null;
        for (Object document : parser.loadAll(exported)) {
            Map<String, Object> manifest = castMap(document);
            if ("VirtualService".equals(manifest.get("kind"))) {
                virtualService = manifest;
                break;
            }
        }

        assertNotNull(virtualService, "Expected VirtualService manifest");
        Map<String, Object> spec = castMap(virtualService.get("spec"));
        List<String> hosts = (List<String>) spec.get("hosts");
        assertEquals("api.example.com", hosts.get(0));
        List<Map<String, Object>> http = (List<Map<String, Object>>) spec.get("http");
        List<Map<String, Object>> routes = (List<Map<String, Object>>) http.get(0).get("route");
        Map<String, Object> destination = castMap(routes.get(0).get("destination"));
        assertEquals("api", destination.get("host"));
        Map<String, Object> port = castMap(destination.get("port"));
        assertEquals(8080, ((Number) port.get("number")).intValue());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private String readZipEntry(ZipInputStream zin) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        int read;
        while ((read = zin.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}
