package com.izylife.izykube.dto.cluster;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceSyncStatusDTO {
    private String resourceId;
    private boolean synced;
    private String message;
}
