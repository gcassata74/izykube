package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PodDTO.class, name = "pod"),
        @JsonSubTypes.Type(value = ContainerDTO.class, name = "container"),
        @JsonSubTypes.Type(value = DeploymentDTO.class, name = "deployment"),
        @JsonSubTypes.Type(value = ServiceDTO.class, name = "service"),
        @JsonSubTypes.Type(value = ConfigMapDTO.class, name = "configmap"),
        @JsonSubTypes.Type(value = VolumeDTO.class, name = "volume"),
        @JsonSubTypes.Type(value = IngressDTO.class, name = "ingress")
})
public abstract class NodeDTO {
    String id;
    String name;
    String kind;
    @Setter
    List<NodeDTO> linkedNodes;

    public NodeDTO(String id, String name, String kind) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.linkedNodes = new ArrayList<>();
    }

}