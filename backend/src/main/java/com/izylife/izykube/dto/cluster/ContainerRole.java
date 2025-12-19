package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ContainerRole {
    INIT,
    SIDECAR;

    @JsonCreator
    public static ContainerRole fromJson(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if ("INIT".equalsIgnoreCase(normalized)) {
            return INIT;
        }
        if ("SIDECAR".equalsIgnoreCase(normalized)) {
            return SIDECAR;
        }
        // Default: any unknown role is treated as a regular (non-init) container.
        return SIDECAR;
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
