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
    private String assetId;
    private Integer containerPort;
    private DeploymentWorkloadType workloadType = DeploymentWorkloadType.DEPLOYMENT;

    public DeploymentDTO(String id, String name, int replicas, String strategyType, String assetId, Integer containerPort) {
        this(id, name, replicas, strategyType, assetId, containerPort, DeploymentWorkloadType.DEPLOYMENT);
    }

    public DeploymentDTO(String id, String name, int replicas, String strategyType, String assetId, Integer containerPort, DeploymentWorkloadType workloadType) {
        super(id, name, "deployment");
        this.replicas = replicas;
        this.strategyType = strategyType;
        this.assetId = assetId;
        this.containerPort = containerPort;
        this.workloadType = workloadType == null ? DeploymentWorkloadType.DEPLOYMENT : workloadType;
    }

    public DeploymentWorkloadType resolveWorkloadType() {
        return workloadType == null ? DeploymentWorkloadType.DEPLOYMENT : workloadType;
    }
}
