package com.izylife.izykube.dto.operator;

import com.izylife.izykube.model.operator.OperatorInstallStatus;
import com.izylife.izykube.model.operator.OperatorUninstallPolicy;
import lombok.Data;

import java.util.List;

@Data
public class OperatorCatalogResponseDTO {
    private String id;
    private String name;
    private String packageName;
    private String channel;
    private String targetNamespace;
    private String desiredVersion;
    private String installedVersion;
    private OperatorUninstallPolicy uninstallPolicy;
    private OperatorInstallStatus status;
    private String lastMessage;
    private String updatedAt;
    private String lastActionAt;
    private String manifestYaml;
    private List<ManagedCrdRefDTO> managedCrds;
}
