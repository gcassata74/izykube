package com.izylife.izykube.dto.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersistentVolumeDTO {

    private String name;
    private String storageClassName;
    private String capacity;
    @Builder.Default
    private List<String> accessModes = new ArrayList<>();
    private String reclaimPolicy;
    private String volumeMode;
    private String path;

    public List<String> getAccessModes() {
        if (accessModes == null) {
            accessModes = new ArrayList<>();
        }
        return accessModes;
    }

    public String getCapacityOrDefault(String defaultValue) {
        return StringUtils.hasText(capacity) ? capacity : defaultValue;
    }
}
