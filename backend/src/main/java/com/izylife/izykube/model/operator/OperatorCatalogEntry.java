package com.izylife.izykube.model.operator;

import com.izylife.izykube.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "operatorCatalog")
public class OperatorCatalogEntry extends BaseEntity {

    private String name;
    private String packageName;
    private String channel;
    private String targetNamespace;
    private String desiredVersion;
    private String installedVersion;
    private OperatorUninstallPolicy uninstallPolicy = OperatorUninstallPolicy.RETAIN_CRDS;
    private OperatorInstallStatus status = OperatorInstallStatus.NOT_INSTALLED;
    private String lastMessage;
    private LocalDateTime lastActionAt;
    private String manifestYaml;
    private List<ManagedCrdRef> managedCrds = new ArrayList<>();
}
