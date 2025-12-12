package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ConfigEntryDTO;
import com.izylife.izykube.dto.cluster.ConfigEntrySensitivity;
import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.DeploymentWorkloadType;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DeploymentProcessorTest {

    private final DeploymentProcessor processor = new DeploymentProcessor(new StubContainerProcessor());

    @Test
    void sanitizesNamesForStatefulSetAndServiceLink() {
        DeploymentDTO stateful = new DeploymentDTO(
                "deployment:mysql",
                "  mysql-db  ",
                1,
                "RollingUpdate",
                "",
                3306,
                DeploymentWorkloadType.STATEFULSET
        );
        stateful.setAssetId("asset-1");
        stateful.setTargetNodes(new ArrayList<>());

        ServiceDTO service = new ServiceDTO("service:mysql", "mysql-service  ", "ClusterIP", 3306);
        service.setTargetNodes(List.of(stateful));
        stateful.setSourceNodes(List.of(service));

        String yaml = processor.createTemplate(stateful);

        Map<String, Object> statefulManifest = null;
        for (Object document : new Yaml().loadAll(yaml)) {
            if (document instanceof Map<?, ?> manifest && "StatefulSet".equals(Objects.toString(manifest.get("kind"), ""))) {
                statefulManifest = castMap(manifest);
                break;
            }
        }

        assertNotNull(statefulManifest, "Expected StatefulSet manifest");
        Map<String, Object> metadata = castMap(statefulManifest.get("metadata"));
        assertEquals("mysql-db", metadata.get("name"));

        Map<String, Object> spec = castMap(statefulManifest.get("spec"));
        assertEquals("mysql-service", spec.get("serviceName"));
        Map<String, Object> selector = castMap(spec.get("selector"));
        Map<String, Object> matchLabels = castMap(selector.get("matchLabels"));
        assertEquals("mysql-db", matchLabels.get("app"));
    }

    @Test
    void envFromUsesSecretRefWhenEntriesContainSecret() {
        DeploymentDTO stateful = new DeploymentDTO(
                "deployment:mysql",
                "mysql-db",
                1,
                "RollingUpdate",
                "",
                3306,
                DeploymentWorkloadType.STATEFULSET
        );
        stateful.setAssetId("asset-1");

        ConfigEntryDTO secretEntry = new ConfigEntryDTO();
        secretEntry.setKey("MYSQL_ROOT_PASSWORD");
        secretEntry.setValue("admin");
        secretEntry.setSensitivity(ConfigEntrySensitivity.SECRET);

        ConfigMapDTO bundle = new ConfigMapDTO("configmap:bundle", "config-bundle-a", null);
        bundle.setEntries(List.of(secretEntry));

        stateful.setSourceNodes(List.of(bundle));
        stateful.setTargetNodes(new ArrayList<>());

        String yaml = processor.createTemplate(stateful);

        Map<String, Object> statefulManifest = null;
        for (Object document : new Yaml().loadAll(yaml)) {
            if (document instanceof Map<?, ?> manifest && "StatefulSet".equals(Objects.toString(manifest.get("kind"), ""))) {
                statefulManifest = castMap(manifest);
                break;
            }
        }

        assertNotNull(statefulManifest, "Expected StatefulSet manifest");
        Map<String, Object> spec = castMap(statefulManifest.get("spec"));
        Map<String, Object> template = castMap(spec.get("template"));
        Map<String, Object> podSpec = castMap(template.get("spec"));
        var containers = (List<Map<String, Object>>) podSpec.get("containers");
        Map<String, Object> container = containers.get(0);
        List<Map<String, Object>> envFrom = (List<Map<String, Object>>) container.get("envFrom");
        Map<String, Object> secretRef = castMap(envFrom.get(0).get("secretRef"));
        assertEquals("config-bundle-a", secretRef.get("name"));
    }

    @Test
    void envFromIncludesBothConfigMapAndSecretForMixedEntries() {
        DeploymentDTO deployment = new DeploymentDTO(
                "deployment:mysql",
                "mysql-db",
                1,
                "RollingUpdate",
                "",
                3306,
                DeploymentWorkloadType.DEPLOYMENT
        );
        deployment.setAssetId("asset-1");

        ConfigEntryDTO plainEntry = new ConfigEntryDTO();
        plainEntry.setKey("DB_USER");
        plainEntry.setValue("root");
        plainEntry.setSensitivity(ConfigEntrySensitivity.PLAIN);

        ConfigEntryDTO secretEntry = new ConfigEntryDTO();
        secretEntry.setKey("DB_PASSWORD");
        secretEntry.setValue("admin");
        secretEntry.setSensitivity(ConfigEntrySensitivity.SECRET);

        ConfigMapDTO bundle = new ConfigMapDTO("configmap:bundle", "config-bundle-a", null);
        bundle.setEntries(List.of(plainEntry, secretEntry));

        deployment.setSourceNodes(List.of(bundle));
        deployment.setTargetNodes(new ArrayList<>());

        String yaml = processor.createTemplate(deployment);

        Map<String, Object> deploymentManifest = null;
        for (Object document : new Yaml().loadAll(yaml)) {
            if (document instanceof Map<?, ?> manifest && "Deployment".equals(Objects.toString(manifest.get("kind"), ""))) {
                deploymentManifest = castMap(manifest);
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

        assertEquals(2, envFrom.size(), "Expected both configMapRef and secretRef");
        Map<String, Object> configMapRef = castMap(envFrom.get(0).get("configMapRef"));
        Map<String, Object> secretRef = castMap(envFrom.get(1).get("secretRef"));
        assertEquals("config-bundle-a", configMapRef.get("name"));
        assertEquals("config-bundle-a", secretRef.get("name"));
    }

    @Test
    void envFromIncludesConfigMapWhenBundleIsSplitAcrossSecretAndConfigMapNodes() {
        DeploymentDTO deployment = new DeploymentDTO(
                "deployment:mysql",
                "mysql-db",
                1,
                "RollingUpdate",
                "",
                3306,
                DeploymentWorkloadType.DEPLOYMENT
        );
        deployment.setAssetId("asset-1");

        ConfigEntryDTO plainEntry = new ConfigEntryDTO();
        plainEntry.setKey("DB_USER");
        plainEntry.setValue("root");
        plainEntry.setSensitivity(ConfigEntrySensitivity.PLAIN);

        ConfigEntryDTO secretEntry = new ConfigEntryDTO();
        secretEntry.setKey("DB_PASSWORD");
        secretEntry.setValue("admin");
        secretEntry.setSensitivity(ConfigEntrySensitivity.SECRET);

        ConfigMapDTO configMapPortion = new ConfigMapDTO("configmap:bundle", "config-bundle-split", null);
        configMapPortion.setEntries(List.of(plainEntry));

        ConfigMapDTO secretPortion = new com.izylife.izykube.dto.cluster.SecretDTO("secret:bundle", "config-bundle-split", null);
        secretPortion.setEntries(List.of(secretEntry));

        // Attach secret first to ensure insertion order does not drop the config map reference
        deployment.setSourceNodes(List.of(secretPortion, configMapPortion));
        deployment.setTargetNodes(new ArrayList<>());

        String yaml = processor.createTemplate(deployment);

        Map<String, Object> deploymentManifest = null;
        for (Object document : new Yaml().loadAll(yaml)) {
            if (document instanceof Map<?, ?> manifest && "Deployment".equals(Objects.toString(manifest.get("kind"), ""))) {
                deploymentManifest = castMap(manifest);
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

        assertNotNull(configMapRef, "Expected a configMapRef entry");
        assertNotNull(secretRef, "Expected a secretRef entry");
        assertEquals("config-bundle-split", configMapRef.get("name"));
        assertEquals("config-bundle-split", secretRef.get("name"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static class StubContainerProcessor extends ContainerProcessor {
        StubContainerProcessor() {
            super(null);
        }

        @Override
        public Container buildPrimaryContainer(DeploymentDTO deployment, List<io.fabric8.kubernetes.api.model.VolumeMount> volumeMounts) {
            return new ContainerBuilder().withName(" db ").build();
        }

        @Override
        public Container processContainer(com.izylife.izykube.dto.cluster.ContainerDTO dto, List<io.fabric8.kubernetes.api.model.VolumeMount> volumeMounts) {
            return new ContainerBuilder().withName(dto.getName()).build();
        }
    }
}
