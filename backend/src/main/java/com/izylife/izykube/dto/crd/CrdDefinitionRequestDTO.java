package com.izylife.izykube.dto.crd;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CrdDefinitionRequestDTO {
    private String group;
    private String singularName;
    private String scope;
    private String version;
    private List<CrdSchemaFieldDTO> schemaFields = new ArrayList<>();
}

