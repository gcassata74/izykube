package com.izylife.izykube.dto.operator;

import lombok.Data;

@Data
public class OperatorCatalogActionRequestDTO {
    private String targetVersion;
    private Boolean force;
}
