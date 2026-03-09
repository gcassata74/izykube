package com.izylife.izykube.dto.operator;

import com.izylife.izykube.model.operator.OperatorUninstallPolicy;
import lombok.Data;

import java.util.List;

@Data
public class OperatorCatalogRequestDTO {
    private String name;
    private String packageName;
    private String channel;
    private String targetNamespace;
    private String desiredVersion;
    private String manifestYaml;
    private OperatorUninstallPolicy uninstallPolicy;
    private List<ManagedCrdRefDTO> managedCrds;
}
