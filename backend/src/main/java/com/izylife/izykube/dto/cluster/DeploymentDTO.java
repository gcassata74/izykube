/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
