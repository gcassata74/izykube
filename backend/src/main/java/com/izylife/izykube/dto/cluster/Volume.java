package com.izylife.izykube.dto.cluster;

import lombok.Data;

@Data
public class Volume extends Base {
    private PersistentVolumeClaim persistentVolumeClaim;
    private ConfigMap configMap;

    // Nested classes for PersistentVolumeClaim and ConfigMap
    @Data
    public static class PersistentVolumeClaim {
        private String claimName;
    }
}