package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.networking.v1.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class IngressDTO extends NodeDTO {
    private String host;
    private String path;

    @JsonCreator
    public IngressDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("host") String host,
            @JsonProperty("path") String path
    ) {
        super(id, name, "ingress");
        this.host = host;
        this.path = path;
    }

    @Override
    public String create(KubernetesClient client) {
        List<IngressRule> rules = new ArrayList<>();

        for (NodeDTO linkedNode : linkedNodes) {
            if (linkedNode instanceof ServiceDTO) {
                ServiceDTO serviceDTO = (ServiceDTO) linkedNode;

                IngressBackend backend = new IngressBackendBuilder()
                        .withService(new IngressServiceBackendBuilder()
                                .withName(serviceDTO.getName())
                                .withPort(new ServiceBackendPortBuilder()
                                        .withNumber(serviceDTO.getPort())
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

                rules.add(ingressRule);
            }
        }

        Ingress ingress = new IngressBuilder()
                .withNewMetadata()
                .withName(getName())
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withRules(rules)
                .endSpec()
                .build();

        return Serialization.asYaml(ingress);
    }
}