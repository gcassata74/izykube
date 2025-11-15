package com.izylife.izykube.services.ai;

import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
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
        cluster.getNodes().add(new DeploymentDTO("web", "web", 2, "RollingUpdate"));

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

