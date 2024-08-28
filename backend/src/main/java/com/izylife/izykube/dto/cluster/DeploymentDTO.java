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
    private DeploymentStrategyDTO strategy;
    private Integer minReadySeconds;
    private Integer revisionHistoryLimit;
    private Integer progressDeadlineSeconds;

    public DeploymentDTO(String id, String name, int replicas, DeploymentStrategyDTO strategy) {
        super(id, name, "deployment");
        this.replicas = replicas;
        this.strategy = strategy;
    }

    public DeploymentDTO(String id, String name, int replicas, DeploymentStrategyDTO strategy,
                         Integer minReadySeconds, Integer revisionHistoryLimit, Integer progressDeadlineSeconds) {
        super(id, name, "deployment");
        this.replicas = replicas;
        this.strategy = strategy;
        this.minReadySeconds = minReadySeconds;
        this.revisionHistoryLimit = revisionHistoryLimit;
        this.progressDeadlineSeconds = progressDeadlineSeconds;
    }
}

