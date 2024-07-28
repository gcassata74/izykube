package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ServiceDTO;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

@Processor(ServiceDTO.class)
@Service
public class ServiceProcessor implements TemplateProcessor<ServiceDTO> {

    public String createTemplate(ServiceDTO dto) {

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
                .withType(dto.getType())
                .withPorts(servicePort)
                .endSpec()
                .build();

        return Serialization.asYaml(service);

    }
}
