package com.izylife.izykube.dto.cluster;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.ArrayList;
import java.util.List;

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
    private boolean addToMesh;
    private DeploymentWorkloadType workloadType = DeploymentWorkloadType.DEPLOYMENT;
    private List<String> command = new ArrayList<>();
    private List<String> args = new ArrayList<>();

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
        this.addToMesh = false;
    }

    public DeploymentWorkloadType resolveWorkloadType() {
        return workloadType == null ? DeploymentWorkloadType.DEPLOYMENT : workloadType;
    }
}
