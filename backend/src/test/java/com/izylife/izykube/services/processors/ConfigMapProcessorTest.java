package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ConfigEntryDTO;
import com.izylife.izykube.dto.cluster.ConfigEntrySensitivity;
import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMapProcessorTest {

    private final ConfigMapProcessor processor = new ConfigMapProcessor();

    @Test
    void createsConfigMapWhenOnlyPlainEntriesPresent() {
        ConfigEntryDTO plain = new ConfigEntryDTO();
        plain.setKey("PLAIN");
        plain.setValue("x");
        plain.setSensitivity(ConfigEntrySensitivity.PLAIN);

        ConfigMapDTO dto = new ConfigMapDTO("configmap:bundle", "config-bundle-a", null);
        dto.setEntries(List.of(plain));
        dto.setNamespace("test-ns");

        String yaml = processor.createTemplate(dto);

        var documents = new Yaml().loadAll(yaml);
        Map<String, Object> configMap = null;
        Map<String, Object> secretManifest = null;
        for (Object doc : documents) {
            if (!(doc instanceof Map<?, ?> map)) {
                continue;
            }
            if ("ConfigMap".equals(map.get("kind"))) {
                configMap = castMap(doc);
            } else if ("Secret".equals(map.get("kind"))) {
                secretManifest = castMap(doc);
            }
        }

        assertTrue(configMap != null, "Expected ConfigMap manifest");
        assertTrue(secretManifest == null, "Did not expect Secret manifest");
        Map<String, Object> configMapData = castMap(configMap.get("data"));
        assertEquals("x", configMapData.get("PLAIN"));
    }

    @Test
    void createsSecretWhenOnlySecretEntriesPresent() {
        ConfigEntryDTO secret = new ConfigEntryDTO();
        secret.setKey("MYSQL_ROOT_PASSWORD");
        secret.setValue("admin");
        secret.setSensitivity(ConfigEntrySensitivity.SECRET);

        ConfigMapDTO dto = new ConfigMapDTO("configmap:bundle", "config-bundle-a", null);
        dto.setEntries(List.of(secret));
        dto.setNamespace("test-ns");

        String yaml = processor.createTemplate(dto);

        var documents = new Yaml().loadAll(yaml);
        Map<String, Object> configMap = null;
        Map<String, Object> secretManifest = null;
        for (Object doc : documents) {
            if (!(doc instanceof Map<?, ?> map)) {
                continue;
            }
            if ("ConfigMap".equals(map.get("kind"))) {
                configMap = castMap(doc);
            } else if ("Secret".equals(map.get("kind"))) {
                secretManifest = castMap(doc);
            }
        }

        assertTrue(configMap == null, "Did not expect ConfigMap manifest");
        assertTrue(secretManifest != null, "Expected Secret manifest");

        Map<String, Object> metadata = castMap(secretManifest.get("metadata"));
        assertEquals("config-bundle-a", metadata.get("name"));

        Map<String, Object> data = castMap(secretManifest.get("data"));
        assertTrue(data.containsKey("MYSQL_ROOT_PASSWORD"));
        assertEquals("YWRtaW4=", data.get("MYSQL_ROOT_PASSWORD"));
    }

    @Test
    void splitsPlainAndSecretEntriesIntoSeparateResources() {
        ConfigEntryDTO plain = new ConfigEntryDTO();
        plain.setKey("PLAIN");
        plain.setValue("x");
        plain.setSensitivity(ConfigEntrySensitivity.PLAIN);

        ConfigEntryDTO secret = new ConfigEntryDTO();
        secret.setKey("MYSQL_ROOT_PASSWORD");
        secret.setValue("admin");
        secret.setSensitivity(ConfigEntrySensitivity.SECRET);

        ConfigMapDTO dto = new ConfigMapDTO("configmap:bundle", "config-bundle-a", null);
        dto.setEntries(List.of(plain, secret));
        dto.setNamespace("test-ns");

        String yaml = processor.createTemplate(dto);

        var documents = new Yaml().loadAll(yaml);
        Map<String, Object> configMap = null;
        Map<String, Object> secretManifest = null;
        for (Object doc : documents) {
            if (!(doc instanceof Map<?, ?> map)) {
                continue;
            }
            if ("ConfigMap".equals(map.get("kind"))) {
                configMap = castMap(doc);
            } else if ("Secret".equals(map.get("kind"))) {
                secretManifest = castMap(doc);
            }
        }

        assertTrue(configMap != null && secretManifest != null, "Expected both ConfigMap and Secret manifests");

        Map<String, Object> configMapData = castMap(configMap.get("data"));
        assertEquals("x", configMapData.get("PLAIN"));
        assertTrue(!configMapData.containsKey("MYSQL_ROOT_PASSWORD"));

        Map<String, Object> metadata = castMap(secretManifest.get("metadata"));
        assertEquals("config-bundle-a", metadata.get("name"));

        Map<String, Object> data = castMap(secretManifest.get("data"));
        assertTrue(data.containsKey("MYSQL_ROOT_PASSWORD"));
        assertEquals("YWRtaW4=", data.get("MYSQL_ROOT_PASSWORD"));
        assertTrue(!data.containsKey("PLAIN"), "Plain entries should be excluded from secret payload");
    }

    @Test
    void doesNotDuplicateKeysAcrossConfigMapAndSecret() {
        ConfigEntryDTO user = new ConfigEntryDTO();
        user.setKey("DB_USER");
        user.setValue("root");
        user.setSensitivity(ConfigEntrySensitivity.PLAIN);

        ConfigEntryDTO name = new ConfigEntryDTO();
        name.setKey("DB_NAME");
        name.setValue("testdb");
        name.setSensitivity(ConfigEntrySensitivity.PLAIN);

        ConfigEntryDTO host = new ConfigEntryDTO();
        host.setKey("DB_HOST");
        host.setValue("mysql-service");
        host.setSensitivity(ConfigEntrySensitivity.PLAIN);

        ConfigEntryDTO password = new ConfigEntryDTO();
        password.setKey("DB_PASSWORD");
        password.setValue("admin");
        password.setSensitivity(ConfigEntrySensitivity.SECRET);

        ConfigMapDTO dto = new ConfigMapDTO("configmap:bundle", "config-bundle-b", null);
        dto.setEntries(List.of(user, name, host, password));
        dto.setNamespace("test-image");

        String yaml = processor.createTemplate(dto);

        Map<String, Object> configMap = null;
        Map<String, Object> secretManifest = null;
        for (Object doc : new Yaml().loadAll(yaml)) {
            if (!(doc instanceof Map<?, ?> map)) {
                continue;
            }
            if ("ConfigMap".equals(map.get("kind"))) {
                configMap = castMap(doc);
            } else if ("Secret".equals(map.get("kind"))) {
                secretManifest = castMap(doc);
            }
        }

        assertTrue(configMap != null && secretManifest != null, "Expected both ConfigMap and Secret manifests");

        Map<String, Object> configMapData = castMap(configMap.get("data"));
        assertEquals(3, configMapData.size());
        assertEquals("root", configMapData.get("DB_USER"));
        assertEquals("testdb", configMapData.get("DB_NAME"));
        assertEquals("mysql-service", configMapData.get("DB_HOST"));
        assertTrue(!configMapData.containsKey("DB_PASSWORD"));

        Map<String, Object> secretData = castMap(secretManifest.get("data"));
        assertEquals(1, secretData.size());
        assertEquals("YWRtaW4=", secretData.get("DB_PASSWORD"));
        assertTrue(!secretData.containsKey("DB_USER"));
        assertTrue(!secretData.containsKey("DB_NAME"));
        assertTrue(!secretData.containsKey("DB_HOST"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
