package com.izylife.izykube.collections;

import lombok.Getter;

@Getter
public enum ClusterStatusEnum {
    INITIALIZED("INITIALIZED"),
    CREATED("CREATED"),
    READY_FOR_DEPLOYMENT("READY_FOR_DEPLOYMENT"),
    DEPLOYED("DEPLOYED");
    private final String value;

    ClusterStatusEnum(String value) {
        this.value = value;
    }

}