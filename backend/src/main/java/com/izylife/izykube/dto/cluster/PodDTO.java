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

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class PodDTO extends NodeDTO {
    private String restartPolicy;
    private String serviceAccountName;
    private Map<String, String> nodeSelector;
    private Boolean hostNetwork;
    private String dnsPolicy;
    private String schedulerName;
    private Integer priority;
    private String preemptionPolicy;

    public PodDTO(String id, String name, String restartPolicy) {
        super(id, name, "pod");
        this.restartPolicy = restartPolicy;
    }

    public PodDTO(String id, String name, String restartPolicy, String serviceAccountName,
                  Map<String, String> nodeSelector, Boolean hostNetwork, String dnsPolicy,
                  String schedulerName, Integer priority, String preemptionPolicy) {
        super(id, name, "pod");
        this.restartPolicy = restartPolicy;
        this.serviceAccountName = serviceAccountName;
        this.nodeSelector = nodeSelector;
        this.hostNetwork = hostNetwork;
        this.dnsPolicy = dnsPolicy;
        this.schedulerName = schedulerName;
        this.priority = priority;
        this.preemptionPolicy = preemptionPolicy;
    }
}
