package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Ingress.class, name = "ingress"),
        @JsonSubTypes.Type(value = PodSpec.class, name = "pod"),
        @JsonSubTypes.Type(value = Container.class, name = "container"),
        @JsonSubTypes.Type(value = Deployment.class, name = "deployment"),
        @JsonSubTypes.Type(value = ConfigMap.class, name = "configMap"),
        @JsonSubTypes.Type(value = Service.class, name = "service"),
        @JsonSubTypes.Type(value = Volume.class, name = "volume"),
})
public class Base {
    String id;
    String name;
    String kind;

    public Base(String id, String name, String kind) {
        this.id = id;
        this.name = name;
        this.kind = kind;
    }
}
