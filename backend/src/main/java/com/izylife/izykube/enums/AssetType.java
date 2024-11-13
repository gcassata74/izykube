package com.izylife.izykube.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum AssetType {
    PLAYBOOK("playbook"),
    IMAGE("image"),
    SCRIPT("script"),
    JVA("jva");

    @JsonValue
    private final String value;

    AssetType(String value) {
        this.value = value;
    }

    public static AssetType fromValue(String value) {
        for (AssetType type : values()) {
            if (type.value.equals(value.toLowerCase())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown AssetType: " + value);
    }

    @Override
    public String toString() {
        return this.value;
    }
}
