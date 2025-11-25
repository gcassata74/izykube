package com.izylife.izykube.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AssetSource {
    BUILT_IN("BUILT_IN"),
    USER("USER");

    private final String value;

    AssetSource(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
