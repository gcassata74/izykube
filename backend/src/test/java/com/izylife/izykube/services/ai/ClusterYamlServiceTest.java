package com.izylife.izykube.services.ai;

import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.SecretDTO;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

        assertTrue(exported.contains("kind: Secret"));
        assertTrue(exported.contains("c3VwZXItc2VjcmV0"));
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
