package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Processor(ServiceDTO.class)
@Service
public class ServiceProcessor implements TemplateProcessor<ServiceDTO> {

    @Override
    public String createTemplate(ServiceDTO dto) {

        Map<String, String> selectors = Optional.ofNullable(dto.getTargetNodes())
                .filter(nodes -> !nodes.isEmpty())
                .flatMap(nodes -> nodes.stream()
                        .filter(node -> node instanceof DeploymentDTO)
                        .findFirst()
                        .map(deployment -> {
                            Map<String, String> map = new HashMap<>();
                            map.put("app", deployment.getName());
                            return map;
                        })
                )
                .orElse(Collections.emptyMap());

        ServicePort servicePort = new ServicePort();
        servicePort.setPort(dto.getPort());
        if ("NodePort".equals(dto.getType()) && dto.getNodePort() != null) {
            servicePort.setNodePort(dto.getNodePort());
        }

        io.fabric8.kubernetes.api.model.Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withSelector(selectors)
                .withType(dto.getType())
                .withPorts(servicePort)

                .endSpec()
                .build();

        return Serialization.asYaml(service);
    }
}