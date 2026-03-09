package com.izylife.izykube.services;

import com.izylife.izykube.dto.crd.CrdDefinitionRequestDTO;
import com.izylife.izykube.dto.crd.CrdDefinitionResponseDTO;
import com.izylife.izykube.dto.crd.CrdDefinitionSummaryResponseDTO;
import com.izylife.izykube.dto.crd.CrdSchemaFieldDTO;
import com.izylife.izykube.enums.CrdFieldType;
import com.izylife.izykube.factory.ClientFactory;
import com.izylife.izykube.model.CrdDefinition;
import com.izylife.izykube.model.CrdSchemaField;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersion;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaProps;
import io.fabric8.kubernetes.client.KubernetesClient;
import com.izylife.izykube.repositories.CrdDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CrdService {

    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    private final CrdDefinitionRepository crdDefinitionRepository;
    private final CrdDerivationService crdDerivationService;
    private final CrdYamlGenerator crdYamlGenerator;
    private final ClientFactory clientFactory;

    public List<CrdDefinitionSummaryResponseDTO> list() {
        return crdDefinitionRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(
                        (CrdDefinition crd) -> safeLower(crd.getSingularName()),
                        Comparator.nullsLast(String::compareTo)
                ))
                .map(this::toSummaryDto)
                .toList();
    }

    public CrdDefinitionResponseDTO get(String id) {
        CrdDefinition crd = crdDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("CRD not found " + id));
        return toDto(crd);
    }

    public List<CrdDefinitionSummaryResponseDTO> listAvailable() {
        List<CrdDefinitionSummaryResponseDTO> available = new ArrayList<>();

        available.addAll(discoverFromCluster().stream()
                .map(this::toClusterSummaryDto)
                .toList());

        available.addAll(list().stream().map(summary -> {
            CrdDefinitionSummaryResponseDTO prefixed = new CrdDefinitionSummaryResponseDTO();
            prefixed.setId("saved:" + summary.getId());
            prefixed.setGroup(summary.getGroup());
            prefixed.setSingularName(summary.getSingularName());
            prefixed.setScope(summary.getScope());
            prefixed.setVersion(summary.getVersion());
            prefixed.setPlural(summary.getPlural());
            prefixed.setKind(summary.getKind());
            prefixed.setMetadataName(summary.getMetadataName());
            prefixed.setUpdatedAt(summary.getUpdatedAt());
            return prefixed;
        }).toList());

        return available;
    }

    public CrdDefinitionResponseDTO getAvailable(String id) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("CRD id is required");
        }
        if (id.startsWith("saved:")) {
            return get(id.substring("saved:".length()));
        }
        if (id.startsWith("cluster:")) {
            ClusterCrdRef ref = parseClusterRef(id);
            CustomResourceDefinition crd = discoverFromCluster().stream()
                    .filter(item -> Objects.equals(resolveMetadataName(item), ref.metadataName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Cluster CRD not found: " + id));
            return toClusterDto(crd, ref.version());
        }
        return get(id);
    }

    public String getYaml(String id) {
        CrdDefinition crd = crdDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("CRD not found " + id));
        validateEntity(crd);
        return crdYamlGenerator.generate(crd);
    }

    public CrdDefinitionResponseDTO create(CrdDefinitionRequestDTO request) {
        CrdDefinition entity = new CrdDefinition();
        applyRequest(entity, request);
        validateEntity(entity);
        CrdDefinition saved = crdDefinitionRepository.save(entity);
        return toDto(saved);
    }

    public CrdDefinitionResponseDTO update(String id, CrdDefinitionRequestDTO request) {
        CrdDefinition existing = crdDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("CRD not found " + id));
        applyRequest(existing, request);
        validateEntity(existing);
        CrdDefinition saved = crdDefinitionRepository.save(existing);
        return toDto(saved);
    }

    public void delete(String id) {
        crdDefinitionRepository.deleteById(id);
    }

    private void applyRequest(CrdDefinition entity, CrdDefinitionRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        entity.setGroup(trimToNull(request.getGroup()));
        entity.setSingularName(trimToNull(request.getSingularName()));
        entity.setScope(trimToNull(request.getScope()));
        entity.setVersion(trimToNull(request.getVersion()) != null ? trimToNull(request.getVersion()) : "v1");

        List<CrdSchemaField> fields = (request.getSchemaFields() == null ? List.<CrdSchemaFieldDTO>of() : request.getSchemaFields())
                .stream()
                .map(this::toEntityField)
                .toList();
        entity.setSchemaFields(fields);
    }

    private CrdSchemaField toEntityField(CrdSchemaFieldDTO dto) {
        CrdSchemaField field = new CrdSchemaField();
        if (dto != null) {
            field.setFieldName(trimToNull(dto.getFieldName()));
            field.setFieldType(dto.getFieldType());
        }
        return field;
    }

    private CrdDefinitionResponseDTO toDto(CrdDefinition entity) {
        CrdDefinitionResponseDTO dto = new CrdDefinitionResponseDTO();
        dto.setId(entity.getId());
        dto.setGroup(entity.getGroup());
        dto.setSingularName(entity.getSingularName());
        dto.setScope(entity.getScope());
        dto.setVersion(entity.getVersion());
        dto.setSchemaFields(entity.getSchemaFields() == null ? List.of() : entity.getSchemaFields().stream().map(this::toDtoField).toList());

        String plural = crdDerivationService.derivePlural(entity.getSingularName());
        dto.setPlural(plural);
        dto.setKind(crdDerivationService.deriveKind(entity.getSingularName()));
        dto.setMetadataName(crdDerivationService.deriveMetadataName(plural, entity.getGroup()));
        dto.setUpdatedAt(entity.getLastUpdated() == null ? null : entity.getLastUpdated().atOffset(ZoneOffset.UTC).toString());
        return dto;
    }

    private CrdDefinitionSummaryResponseDTO toSummaryDto(CrdDefinition entity) {
        CrdDefinitionSummaryResponseDTO dto = new CrdDefinitionSummaryResponseDTO();
        dto.setId(entity.getId());
        dto.setGroup(entity.getGroup());
        dto.setSingularName(entity.getSingularName());
        dto.setScope(entity.getScope());
        dto.setVersion(entity.getVersion());

        String plural = crdDerivationService.derivePlural(entity.getSingularName());
        dto.setPlural(plural);
        dto.setKind(crdDerivationService.deriveKind(entity.getSingularName()));
        dto.setMetadataName(crdDerivationService.deriveMetadataName(plural, entity.getGroup()));
        dto.setUpdatedAt(entity.getLastUpdated() == null ? null : entity.getLastUpdated().atOffset(ZoneOffset.UTC).toString());
        return dto;
    }

    private CrdSchemaFieldDTO toDtoField(CrdSchemaField field) {
        CrdSchemaFieldDTO dto = new CrdSchemaFieldDTO();
        dto.setFieldName(field.getFieldName());
        dto.setFieldType(field.getFieldType());
        return dto;
    }

    private void validateEntity(CrdDefinition entity) {
        if (!StringUtils.hasText(entity.getGroup())) {
            throw new IllegalArgumentException("group is required");
        }
        if (!StringUtils.hasText(entity.getSingularName())) {
            throw new IllegalArgumentException("singularName is required");
        }
        if (!StringUtils.hasText(entity.getScope())) {
            throw new IllegalArgumentException("scope is required");
        }
        if (!StringUtils.hasText(entity.getVersion())) {
            throw new IllegalArgumentException("version is required");
        }

        Set<String> seen = new HashSet<>();
        if (entity.getSchemaFields() != null) {
            for (CrdSchemaField field : entity.getSchemaFields()) {
                if (field == null) {
                    continue;
                }
                String name = trimToNull(field.getFieldName());
                if (name == null) {
                    throw new IllegalArgumentException("schemaFields.fieldName is required");
                }
                if (!FIELD_NAME_PATTERN.matcher(name).matches()) {
                    throw new IllegalArgumentException("Invalid schemaFields.fieldName: " + name);
                }
                String normalized = name.toLowerCase(Locale.ROOT);
                if (!seen.add(normalized)) {
                    throw new IllegalArgumentException("Duplicate schema fieldName: " + name);
                }
                if (field.getFieldType() == null) {
                    throw new IllegalArgumentException("schemaFields.fieldType is required for " + name);
                }
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeLower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private List<CustomResourceDefinition> discoverFromCluster() {
        KubernetesClient kubernetesClient = (KubernetesClient) clientFactory.getClient("kubernetes");
        try {
            List<CustomResourceDefinition> items = kubernetesClient
                    .apiextensions()
                    .v1()
                    .customResourceDefinitions()
                    .list()
                    .getItems();
            return items == null ? List.of() : items;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private CrdDefinitionSummaryResponseDTO toClusterSummaryDto(CustomResourceDefinition crd) {
        CustomResourceDefinitionVersion version = pickVersion(crd, null);
        String versionName = version == null ? "v1" : version.getName();
        CrdDefinitionSummaryResponseDTO dto = new CrdDefinitionSummaryResponseDTO();
        dto.setId(buildClusterId(resolveMetadataName(crd), versionName));
        dto.setGroup(crd.getSpec().getGroup());
        dto.setSingularName(resolveSingularName(crd));
        dto.setScope(crd.getSpec().getScope());
        dto.setVersion(versionName);
        dto.setPlural(crd.getSpec().getNames().getPlural());
        dto.setKind(crd.getSpec().getNames().getKind());
        dto.setMetadataName(resolveMetadataName(crd));
        return dto;
    }

    private CrdDefinitionResponseDTO toClusterDto(CustomResourceDefinition crd, String versionName) {
        CrdDefinitionResponseDTO dto = new CrdDefinitionResponseDTO();
        dto.setId(buildClusterId(resolveMetadataName(crd), versionName));
        dto.setGroup(crd.getSpec().getGroup());
        dto.setSingularName(resolveSingularName(crd));
        dto.setScope(crd.getSpec().getScope());
        dto.setVersion(versionName);
        dto.setPlural(crd.getSpec().getNames().getPlural());
        dto.setKind(crd.getSpec().getNames().getKind());
        dto.setMetadataName(resolveMetadataName(crd));
        dto.setSchemaFields(extractSchemaFields(crd, versionName));
        return dto;
    }

    private List<CrdSchemaFieldDTO> extractSchemaFields(CustomResourceDefinition crd, String versionName) {
        CustomResourceDefinitionVersion version = pickVersion(crd, versionName);
        if (version == null || version.getSchema() == null || version.getSchema().getOpenAPIV3Schema() == null) {
            return List.of();
        }

        JSONSchemaProps rootSchema = version.getSchema().getOpenAPIV3Schema();
        Map<String, JSONSchemaProps> rootProperties = rootSchema.getProperties();
        if (rootProperties == null || rootProperties.isEmpty()) {
            return List.of();
        }

        JSONSchemaProps specSchema = rootProperties.get("spec");
        if (specSchema == null || specSchema.getProperties() == null || specSchema.getProperties().isEmpty()) {
            return List.of();
        }

        Map<String, JSONSchemaProps> specProperties = new LinkedHashMap<>(specSchema.getProperties());
        return specProperties.entrySet().stream()
                .map(entry -> {
                    CrdSchemaFieldDTO field = new CrdSchemaFieldDTO();
                    field.setFieldName(entry.getKey());
                    field.setFieldType(resolveFieldType(entry.getValue()));
                    return field;
                })
                .toList();
    }

    private CrdFieldType resolveFieldType(JSONSchemaProps schema) {
        if (schema == null) {
            return CrdFieldType.STRING;
        }
        String type = schema.getType();
        if (!StringUtils.hasText(type)) {
            if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
                return CrdFieldType.OBJECT;
            }
            if (schema.getItems() != null) {
                return CrdFieldType.ARRAY;
            }
            return CrdFieldType.STRING;
        }
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "number", "integer" -> CrdFieldType.NUMBER;
            case "boolean" -> CrdFieldType.BOOLEAN;
            case "object" -> CrdFieldType.OBJECT;
            case "array" -> CrdFieldType.ARRAY;
            default -> CrdFieldType.STRING;
        };
    }

    private CustomResourceDefinitionVersion pickVersion(CustomResourceDefinition crd, String versionName) {
        if (crd == null || crd.getSpec() == null || crd.getSpec().getVersions() == null || crd.getSpec().getVersions().isEmpty()) {
            return null;
        }
        List<CustomResourceDefinitionVersion> versions = crd.getSpec().getVersions();
        if (StringUtils.hasText(versionName)) {
            return versions.stream()
                    .filter(v -> versionName.equals(v.getName()))
                    .findFirst()
                    .orElse(versions.get(0));
        }
        return versions.stream()
                .filter(v -> Boolean.TRUE.equals(v.getStorage()))
                .findFirst()
                .orElse(versions.get(0));
    }

    private String resolveMetadataName(CustomResourceDefinition crd) {
        String metadataName = crd.getMetadata() == null ? null : crd.getMetadata().getName();
        if (StringUtils.hasText(metadataName)) {
            return metadataName;
        }
        String plural = crd.getSpec() != null && crd.getSpec().getNames() != null ? crd.getSpec().getNames().getPlural() : null;
        String group = crd.getSpec() != null ? crd.getSpec().getGroup() : null;
        return (plural == null ? "" : plural) + "." + (group == null ? "" : group);
    }

    private String resolveSingularName(CustomResourceDefinition crd) {
        if (crd == null || crd.getSpec() == null || crd.getSpec().getNames() == null) {
            return null;
        }
        String singular = crd.getSpec().getNames().getSingular();
        if (StringUtils.hasText(singular)) {
            return singular;
        }
        String kind = crd.getSpec().getNames().getKind();
        return kind == null ? null : kind.toLowerCase(Locale.ROOT);
    }

    private String buildClusterId(String metadataName, String version) {
        return "cluster:" + metadataName + ":" + version;
    }

    private ClusterCrdRef parseClusterRef(String id) {
        String raw = id.substring("cluster:".length());
        int idx = raw.lastIndexOf(':');
        if (idx < 0) {
            throw new IllegalArgumentException("Invalid cluster CRD id: " + id);
        }
        String metadataName = raw.substring(0, idx);
        String version = raw.substring(idx + 1);
        if (!StringUtils.hasText(metadataName) || !StringUtils.hasText(version)) {
            throw new IllegalArgumentException("Invalid cluster CRD id: " + id);
        }
        return new ClusterCrdRef(metadataName, version);
    }

    private record ClusterCrdRef(String metadataName, String version) {}
}
