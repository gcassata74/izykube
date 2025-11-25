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
    private ContainerRole role;

    public ContainerDTO(String id, String name, String assetId, int containerPort) {
        this(id, name, assetId, containerPort, null);
    }

    public ContainerDTO(String id, String name, String assetId, int containerPort, ContainerRole role) {
        super(id, name, "container");
        this.assetId = assetId;
        this.containerPort = containerPort;
        this.role = role;
    }
}
