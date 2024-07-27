package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PodDTO.class, name = "pod"),
        @JsonSubTypes.Type(value = ContainerDTO.class, name = "container"),
        @JsonSubTypes.Type(value = DeploymentDTO.class, name = "deployment"),
        @JsonSubTypes.Type(value = ServiceDTO.class, name = "service"),
        @JsonSubTypes.Type(value = ConfigMapDTO.class, name = "configMap")
})
public abstract class NodeDTO {
    String id;
    String name;
    String kind;
    String namespace = "default";
    List<NodeDTO> linkedNodes;

    public NodeDTO(String id, String name, String kind) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        linkedNodes = new ArrayList<>();
    }

}
