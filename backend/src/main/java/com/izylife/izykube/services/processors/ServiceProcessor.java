package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Processor(ServiceDTO.class)
@Service
public class ServiceProcessor implements TemplateProcessor<ServiceDTO> {

    @Override
    public String createTemplate(ServiceDTO dto) {


        DeploymentDTO deploymentDTO = dto.getSourceNodes().stream()
                .filter(DeploymentDTO.class::isInstance)
                .map(DeploymentDTO.class::cast)
                .findFirst()
                .orElse(null);

        ContainerDTO containerDTO = deploymentDTO.getSourceNodes().stream()
                .filter(ContainerDTO.class::isInstance)
                .map(ContainerDTO.class::cast)
                .findFirst()
                .orElse(null);


        Map<String, String> selectors = dto.getSourceNodes().stream()
                .filter(DeploymentDTO.class::isInstance)
                .map(DeploymentDTO.class::cast)
                .findFirst()
                .map(deployment -> Collections.singletonMap("app", deployment.getName()))
                .orElse(Collections.emptyMap());

        ServicePort servicePort = new ServicePort();
        servicePort.setPort(dto.getPort());
        servicePort.setTargetPort(new IntOrString(containerDTO.getContainerPort()));

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