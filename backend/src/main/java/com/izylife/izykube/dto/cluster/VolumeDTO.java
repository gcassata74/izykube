package com.izylife.izykube.dto.cluster;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class VolumeDTO extends NodeDTO {
    private String type;
    private Map<String, Object> config;

    public VolumeDTO(String id, String name, String type, Map<String, Object> config) {
        super(id, name, "volume");
        this.type = type;
        this.config = config;
    }

    // Convenience methods for type-specific configurations
    public void setEmptyDirConfig(String mountPath, String medium, String sizeLimit) {
        this.type = "emptyDir";
        this.config = new HashMap<>();
        this.config.put("type", "emptyDir");
        this.config.put("mountPath", mountPath);
        this.config.put("medium", medium);
        this.config.put("sizeLimit", sizeLimit);
    }

    public void setHostPathConfig(String mountPath, String path, String hostPathType) {
        this.type = "hostPath";
        this.config = new HashMap<>();
        this.config.put("type", "hostPath");
        this.config.put("mountPath", mountPath);
        this.config.put("path", path);
        this.config.put("hostPathType", hostPathType);
    }

    public void setConfigMapConfig(String mountPath, String name, Map<String, String> items) {
        this.type = "configMap";
        this.config = new HashMap<>();
        this.config.put("type", "configMap");
        this.config.put("mountPath", mountPath);
        this.config.put("name", name);
        this.config.put("items", items);
    }

    public void setSecretConfig(String mountPath, String secretName, Map<String, String> items) {
        this.type = "secret";
        this.config = new HashMap<>();
        this.config.put("type", "secret");
        this.config.put("mountPath", mountPath);
        this.config.put("secretName", secretName);
        this.config.put("items", items);
    }

    public void setPersistentVolumeClaimConfig(String mountPath, String claimName, Boolean readOnly) {
        this.type = "persistentVolumeClaim";
        this.config = new HashMap<>();
        this.config.put("type", "persistentVolumeClaim");
        this.config.put("mountPath", mountPath);
        this.config.put("claimName", claimName);
        this.config.put("readOnly", readOnly);
    }
}