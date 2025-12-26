package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ServiceAccountDTO;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceAccountProcessorTest {

    private final ServiceAccountProcessor processor = new ServiceAccountProcessor();

    @Test
    void generatesServiceAccountManifest() {
        ServiceAccountDTO dto = new ServiceAccountDTO("sa-1", "example-sa");
        dto.setNamespace("test-ns");
        dto.setAutomountServiceAccountToken(false);

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("app", "demo");
        dto.setLabels(labels);

        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("note", "x");
        dto.setAnnotations(annotations);

        String yaml = processor.createTemplate(dto);
        Map<String, Object> document = castMap(new Yaml().load(yaml));

        assertEquals("v1", document.get("apiVersion"));
        assertEquals("ServiceAccount", document.get("kind"));

        Map<String, Object> metadata = castMap(document.get("metadata"));
        assertEquals("example-sa", metadata.get("name"));
        assertEquals("test-ns", metadata.get("namespace"));
        assertEquals("demo", castMap(metadata.get("labels")).get("app"));
        assertEquals("x", castMap(metadata.get("annotations")).get("note"));

        assertEquals(false, document.get("automountServiceAccountToken"));
    }

    @Test
    void rejectsInvalidServiceAccountName() {
        ServiceAccountDTO dto = new ServiceAccountDTO("sa-1", "Example-SA");
        dto.setNamespace("test-ns");
        assertThrows(IllegalArgumentException.class, () -> processor.createTemplate(dto));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        assertNotNull(value);
        return (Map<String, Object>) value;
    }
}
