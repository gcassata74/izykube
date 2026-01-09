package com.izylife.izykube.services;

import com.izylife.izykube.enums.CrdFieldType;
import com.izylife.izykube.model.CrdDefinition;
import com.izylife.izykube.model.CrdSchemaField;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CrdYamlGenerator {

    private final CrdDerivationService derivationService;
    private final Yaml yaml;

    public CrdYamlGenerator(CrdDerivationService derivationService) {
        this.derivationService = derivationService;
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        this.yaml = new Yaml(options);
    }

    public String generate(CrdDefinition crdDefinition) {
        Objects.requireNonNull(crdDefinition, "crdDefinition is required");
        String plural = derivationService.derivePlural(crdDefinition.getSingularName());
        String kind = derivationService.deriveKind(crdDefinition.getSingularName());
        String metadataName = derivationService.deriveMetadataName(plural, crdDefinition.getGroup());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("apiVersion", "apiextensions.k8s.io/v1");
        root.put("kind", "CustomResourceDefinition");

        root.put("metadata", Map.of("name", metadataName));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("group", crdDefinition.getGroup());
        spec.put("scope", crdDefinition.getScope());
        spec.put("names", Map.of(
                "plural", plural,
                "singular", crdDefinition.getSingularName(),
                "kind", kind
        ));

        Map<String, Object> openApi = new LinkedHashMap<>();
        openApi.put("type", "object");

        Map<String, Object> specSchema = new LinkedHashMap<>();
        specSchema.put("type", "object");

        Map<String, Object> fieldProperties = new LinkedHashMap<>();
        for (CrdSchemaField field : safeList(crdDefinition.getSchemaFields())) {
            String fieldName = field.getFieldName();
            if (fieldName == null || fieldName.isBlank() || field.getFieldType() == null) {
                continue;
            }
            fieldProperties.put(fieldName, schemaForType(field.getFieldType()));
        }
        if (!fieldProperties.isEmpty()) {
            specSchema.put("properties", fieldProperties);
        }

        openApi.put("properties", Map.of("spec", specSchema));

        Map<String, Object> versionEntry = new LinkedHashMap<>();
        versionEntry.put("name", crdDefinition.getVersion());
        versionEntry.put("served", true);
        versionEntry.put("storage", true);
        versionEntry.put("schema", Map.of("openAPIV3Schema", openApi));

        spec.put("versions", List.of(versionEntry));
        root.put("spec", spec);

        return yaml.dump(root);
    }

    private Map<String, Object> schemaForType(CrdFieldType fieldType) {
        return Map.of("type", fieldType.getValue());
    }

    private List<CrdSchemaField> safeList(List<CrdSchemaField> fields) {
        return fields == null ? List.of() : fields;
    }
}

