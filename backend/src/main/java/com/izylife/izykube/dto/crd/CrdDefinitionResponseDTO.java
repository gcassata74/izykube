package com.izylife.izykube.dto.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrdDefinitionResponseDTO {
    private String id;
    private String group;
    private String singularName;
    private String scope;
    private String version;
    private List<CrdSchemaFieldDTO> schemaFields = new ArrayList<>();

    // Derived preview fields
    private String plural;
    private String kind;
    private String metadataName;

    private String updatedAt;
}

