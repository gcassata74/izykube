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
    private String serviceAccountRef;
    private String serviceAccountName;
    private DeploymentWorkloadType workloadType = DeploymentWorkloadType.DEPLOYMENT;

    public DeploymentDTO(String id, String name, int replicas, String strategyType, String assetId, Integer containerPort) {
        this(id, name, replicas, strategyType, assetId, containerPort, DeploymentWorkloadType.DEPLOYMENT, null);
    }

    public DeploymentDTO(String id, String name, int replicas, String strategyType, String assetId, Integer containerPort, DeploymentWorkloadType workloadType) {
        this(id, name, replicas, strategyType, assetId, containerPort, workloadType, null);
    }

    public DeploymentDTO(String id, String name, int replicas, String strategyType, String assetId, Integer containerPort, DeploymentWorkloadType workloadType, String serviceAccountRef) {
        super(id, name, "deployment");
        this.replicas = replicas;
        this.strategyType = strategyType;
        this.assetId = assetId;
        this.containerPort = containerPort;
        this.workloadType = workloadType == null ? DeploymentWorkloadType.DEPLOYMENT : workloadType;
        this.serviceAccountRef = serviceAccountRef;
    }

    public DeploymentWorkloadType resolveWorkloadType() {
        return workloadType == null ? DeploymentWorkloadType.DEPLOYMENT : workloadType;
    }
}
