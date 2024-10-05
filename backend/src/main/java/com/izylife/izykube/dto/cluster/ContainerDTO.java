package com.izylife.izykube.dto.cluster;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ContainerDTO extends NodeDTO {
    private String assetId;
    private int containerPort;
    private boolean isSidecar;

    public ContainerDTO(String id, String name, String assetId, int containerPort, boolean isSidecar) {
        super(id, name, "container");
        this.assetId = assetId;
        this.containerPort = containerPort;
        this.isSidecar = isSidecar;
    }
}