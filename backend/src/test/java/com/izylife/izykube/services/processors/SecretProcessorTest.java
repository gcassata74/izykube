package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.SecretDTO;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretProcessorTest {

    private final SecretProcessor processor = new SecretProcessor();

    @Test
    void skipsEmptySecretData() {
        SecretDTO dto = new SecretDTO("secret:empty", "empty-secret", "");
        String template = processor.createTemplate(dto);
        assertTrue(template.isEmpty(), "Empty secret payloads must not generate manifests");
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
}
