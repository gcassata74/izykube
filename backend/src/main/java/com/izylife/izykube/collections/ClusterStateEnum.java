package com.izylife.izykube.collections;

import lombok.Getter;

@Getter
public enum ClusterStateEnum {
    CREATED("CREATED"),
    READY_FOR_DEPLOYMENT("READY_FOR_DEPLOYMENT"),
    RUNNING("RUNNING");
    private final String value;

    ClusterStateEnum(String value) {
        this.value = value;
    }

}