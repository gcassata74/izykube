package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.networking.v1.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IngressDTO extends NodeDTO {
    private String host;
    private String path;
    private int servicePort;
    private String serviceTarget;

    @JsonCreator
    public IngressDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("host") String host,
            @JsonProperty("path") String path,
            @JsonProperty("servicePort") int servicePort,
            @JsonProperty("serviceTarget") String serviceTarget
    ) {
        super(id, name, "ingress");
        this.host = host;
        this.path = path;
        this.servicePort = servicePort;
        this.serviceTarget = serviceTarget;
    }

    @Override
    public String create(KubernetesClient client) {
        IngressBackend backend = new IngressBackendBuilder()
                .withService(new IngressServiceBackendBuilder()
                        .withName(serviceTarget)
                        .withPort(new ServiceBackendPortBuilder()
                                .withNumber(servicePort)
                                .build())
                        .build())
                .build();

        HTTPIngressPath httpIngressPath = new HTTPIngressPathBuilder()
                .withPath(path)
                .withPathType("Prefix")
                .withBackend(backend)
                .build();

        IngressRule ingressRule = new IngressRuleBuilder()
                .withHost(host)
                .withHttp(new HTTPIngressRuleValueBuilder()
                        .withPaths(httpIngressPath)
                        .build())
                .build();

        Ingress ingress = new IngressBuilder()
                .withNewMetadata()
                .withName(getName())
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withRules(ingressRule)
                .endSpec()
                .build();

        return Serialization.asYaml(ingress);
    }
}