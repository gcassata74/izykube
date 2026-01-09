package com.izylife.izykube.dto.crd;

import com.izylife.izykube.enums.CrdFieldType;
import lombok.Data;

@Data
public class CrdSchemaFieldDTO {
    private String fieldName;
    private CrdFieldType fieldType;
}

