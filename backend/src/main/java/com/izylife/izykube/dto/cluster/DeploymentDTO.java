package com.izylife.izykube.dto.cluster;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentDTO extends NodeDTO {
    private int replicas;
    private String strategyType;

    public DeploymentDTO(String id, String name, int replicas, String strategyType) {
        super(id, name, "deployment");
        this.replicas = replicas;
        this.strategyType = strategyType;
    }
}

