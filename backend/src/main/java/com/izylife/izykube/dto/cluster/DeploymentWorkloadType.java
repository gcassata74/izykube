package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum DeploymentWorkloadType {
    DEPLOYMENT,
    STATEFULSET,
    DAEMONSET;

    @JsonCreator
    public static DeploymentWorkloadType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return DEPLOYMENT;
        }
        try {
            return DeploymentWorkloadType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DEPLOYMENT;
        }
    }
}
