package com.izylife.izykube.dto.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrdDefinitionSummaryResponseDTO {
    private String id;
    private String group;
    private String singularName;
    private String scope;
    private String version;

    // Derived summary fields
    private String plural;
    private String kind;
    private String metadataName;

    private String updatedAt;
}

