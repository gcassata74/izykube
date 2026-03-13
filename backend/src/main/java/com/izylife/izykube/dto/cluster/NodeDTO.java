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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.izylife.izykube.dto.cluster.LinkDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Transient;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PodDTO.class, name = "pod"),
        @JsonSubTypes.Type(value = ContainerDTO.class, name = "container"),
        @JsonSubTypes.Type(value = DeploymentDTO.class, name = "deployment"),
        @JsonSubTypes.Type(value = ServiceDTO.class, name = "service"),
        @JsonSubTypes.Type(value = ConfigMapDTO.class, name = "configmap"),
        @JsonSubTypes.Type(value = SecretDTO.class, name = "secret"),
        @JsonSubTypes.Type(value = VolumeDTO.class, name = "volume"),
        @JsonSubTypes.Type(value = IngressDTO.class, name = "ingress"),
        @JsonSubTypes.Type(value = VirtualServiceDTO.class, name = "istio"),
        @JsonSubTypes.Type(value = JobDTO.class, name = "job"),
        @JsonSubTypes.Type(value = CustomResourceDTO.class, name = "cr"),
        @JsonSubTypes.Type(value = ServiceAccountDTO.class, name = "serviceaccount"),
        @JsonSubTypes.Type(value = AccessPolicyDTO.class, name = "accesspolicy")
})
public abstract class NodeDTO {
    @JsonProperty("id")
    String id;
    @JsonProperty("name")
    String name;
    @JsonProperty("kind")
    String kind;
    @JsonIgnore
    String namespace;
    @Setter
    @Transient
    @JsonIgnore
    List<NodeDTO> sourceNodes;
    @Setter
    @Transient
    @JsonIgnore
    List<NodeDTO> targetNodes;
    @Setter
    @Transient
    @JsonIgnore
    List<LinkDTO> incomingLinks;
    @Setter
    @Transient
    @JsonIgnore
    List<LinkDTO> outgoingLinks;
    @Setter
    @Transient
    @JsonIgnore
    Map<String, NodeDTO> nodeIndex;
    @JsonProperty("isAffected")
    private boolean affected;

    public NodeDTO(String id, String name, String kind) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.sourceNodes = new ArrayList<>();
        this.targetNodes = new ArrayList<>();
        this.incomingLinks = new ArrayList<>();
        this.outgoingLinks = new ArrayList<>();
    }

}
