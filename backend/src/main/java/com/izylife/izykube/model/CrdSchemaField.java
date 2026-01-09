package com.izylife.izykube.model;

import com.izylife.izykube.enums.CrdFieldType;
import lombok.Data;

@Data
public class CrdSchemaField {
    private String fieldName;
    private CrdFieldType fieldType;
}

