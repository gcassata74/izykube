package com.izylife.izykube.services;

import com.izylife.izykube.dto.crd.CrdDefinitionRequestDTO;
import com.izylife.izykube.dto.crd.CrdDefinitionResponseDTO;
import com.izylife.izykube.dto.crd.CrdDefinitionSummaryResponseDTO;
import com.izylife.izykube.dto.crd.CrdSchemaFieldDTO;
import com.izylife.izykube.model.CrdDefinition;
import com.izylife.izykube.model.CrdSchemaField;
import com.izylife.izykube.repositories.CrdDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CrdService {

    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    private final CrdDefinitionRepository crdDefinitionRepository;
    private final CrdDerivationService crdDerivationService;
    private final CrdYamlGenerator crdYamlGenerator;

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
}
