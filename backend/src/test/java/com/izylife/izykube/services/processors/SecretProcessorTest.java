package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ConfigEntryDTO;
import com.izylife.izykube.dto.cluster.ConfigEntrySensitivity;
import com.izylife.izykube.dto.cluster.SecretDTO;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretProcessorTest {

    private final SecretProcessor processor = new SecretProcessor();

    @Test
    void rendersEmptySecretWhenNoData() {
        SecretDTO dto = new SecretDTO("secret:empty", "empty-secret", "");
        String template = processor.createTemplate(dto);
        assertFalse(template.isEmpty(), "Empty secret payloads should still generate manifests");
        var manifest = new Yaml().load(template);
        var metadata = (java.util.Map<String, Object>) ((java.util.Map<String, Object>) manifest).get("metadata");
        assertTrue("empty-secret".equals(metadata.get("name")));
        var dataSection = (java.util.Map<String, Object>) ((java.util.Map<String, Object>) manifest).get("data");
        assertTrue(dataSection == null || dataSection.isEmpty(), "Empty secrets should render no data entries");
    }

    @Test
    void encodesSecretValuesWhenPresent() {
        SecretDTO dto = new SecretDTO("secret:db", "db-secret", "password: admin\n");
        String template = processor.createTemplate(dto);
        assertFalse(template.isEmpty(), "Non-empty secret payloads should be rendered");
        var manifest = new Yaml().load(template);
        Object dataSection = manifest instanceof java.util.Map<?, ?> map ? map.get("data") : null;
        assertTrue(dataSection instanceof java.util.Map, "Secret manifest must include data section");
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) dataSection;
        assertTrue("YWRtaW4=".equals(data.get("password")), "Password should be base64-encoded");
    }

    @Test
    void trimsSecretNameAndKeys() {
        SecretDTO dto = new SecretDTO("secret:spaced", "  spaced-secret  ", "");
        ConfigEntryDTO entry = new ConfigEntryDTO();
        entry.setKey("  api-key  ");
        entry.setValue("token");
        entry.setSensitivity(ConfigEntrySensitivity.SECRET);
        dto.setEntries(java.util.List.of(entry));

        String template = processor.createTemplate(dto);
        assertFalse(template.isEmpty());

        var manifest = new Yaml().load(template);
        var metadata = (java.util.Map<String, Object>) ((java.util.Map<String, Object>) manifest).get("metadata");
        assertTrue("spaced-secret".equals(metadata.get("name")));
        var dataSection = (java.util.Map<String, Object>) ((java.util.Map<String, Object>) manifest).get("data");
        assertTrue(dataSection.containsKey("api-key"), "Secret key should be trimmed");
    }
}
