package com.izylife.izykube.dto.operator;

import lombok.Data;

@Data
public class ManagedCrdRefDTO {
    private String group;
    private String version;
    private String plural;
    private Boolean namespaced;
}
