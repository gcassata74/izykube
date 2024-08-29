package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.IngressDTO;
import io.fabric8.kubernetes.api.model.networking.v1.*;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Processor(IngressDTO.class)
@Service
public class IngressProcessor implements TemplateProcessor<IngressDTO> {

    @Override
    public String createTemplate(IngressDTO dto) {
        List<IngressRule> rules = new ArrayList<>();

        HTTPIngressPath httpIngressPath = new HTTPIngressPathBuilder()
                .withPath(dto.getPath())
                .withPathType("Prefix")
                .withBackend(new IngressBackendBuilder()
                        .withService(new IngressServiceBackendBuilder()
                                .withName(dto.getServiceName())
                                .withPort(new ServiceBackendPortBuilder()
                                        .withNumber(dto.getServicePort())
                                        .build())
                                .build())
                        .build())
                .build();

        IngressRule ingressRule = new IngressRuleBuilder()
                .withHost(dto.getHost())
                .withHttp(new HTTPIngressRuleValueBuilder()
                        .withPaths(httpIngressPath)
                        .build())
                .build();

        rules.add(ingressRule);

        Ingress ingress = new IngressBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withRules(rules)
                .endSpec()
                .build();

        return Serialization.asYaml(ingress);
    }
}