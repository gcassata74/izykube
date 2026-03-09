package com.izylife.izykube.model.operator;

import lombok.Data;

@Data
public class ManagedCrdRef {
    private String group;
    private String version;
    private String plural;
    private Boolean namespaced = Boolean.TRUE;
}
