package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Transient;

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
        @JsonSubTypes.Type(value = VolumeDTO.class, name = "volume"),
        @JsonSubTypes.Type(value = IngressDTO.class, name = "ingress")
})
public abstract class NodeDTO {
    @JsonProperty("id")
    String id;
    @JsonProperty("name")
    String name;
    @JsonProperty("kind")
    String kind;
    @Setter
    @Transient
    @JsonIgnore
    List<NodeDTO> sourceNodes;
    @Setter
    @Transient
    @JsonIgnore
    List<NodeDTO> targetNodes;

    public NodeDTO(String id, String name, String kind) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.sourceNodes = new ArrayList<>();
        this.targetNodes = new ArrayList<>();
    }

}