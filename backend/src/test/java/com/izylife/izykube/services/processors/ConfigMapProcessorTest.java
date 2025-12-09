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
    void promotesSecretEntriesToSecretResource() {
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
        dto.setSecret(true);

        String yaml = processor.createTemplate(dto);

        Map<String, Object> manifest = new Yaml().load(yaml);
        assertEquals("Secret", manifest.get("kind"));
        Map<String, Object> metadata = castMap(manifest.get("metadata"));
        assertEquals("config-bundle-a", metadata.get("name"));
        Map<String, Object> data = castMap(manifest.get("data"));
        assertTrue(data.containsKey("MYSQL_ROOT_PASSWORD"));
        assertEquals("YWRtaW4=", data.get("MYSQL_ROOT_PASSWORD"));
        assertTrue(!data.containsKey("PLAIN"), "Plain entries should be excluded from secret payload");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
