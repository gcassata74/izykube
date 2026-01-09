package com.izylife.izykube.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum CrdFieldType {
    STRING("string"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    OBJECT("object"),
    ARRAY("array");

    @JsonValue
    private final String value;

    CrdFieldType(String value) {
        this.value = value;
    }

    public static CrdFieldType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("fieldType is required");
        }
        for (CrdFieldType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown fieldType: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}

