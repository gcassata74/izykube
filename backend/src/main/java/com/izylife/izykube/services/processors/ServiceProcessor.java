package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import io.fabric8.istio.api.networking.v1beta1.*;
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
        StringBuilder yaml = new StringBuilder();
        String namespace = resolveNamespace(dto);

        // Create Kubernetes Service
        yaml.append(createKubernetesService(dto, namespace));

        // Always create VirtualService
        yaml.append(createVirtualService(dto, namespace));

        // If service is exposed, create Gateway
        if (dto.isExposeService() && dto.getFrontendUrl() != null && !dto.getFrontendUrl().isEmpty()) {
            yaml.append(createGateway(dto, namespace));
        }

        return yaml.toString();
    }

    private String createKubernetesService(ServiceDTO dto, String namespace) {
        DeploymentDTO deploymentDTO = dto.getTargetNodes().stream()
                .filter(DeploymentDTO.class::isInstance)
                .map(DeploymentDTO.class::cast)
                .findFirst()
                .orElse(null);

        if (deploymentDTO == null) {
            throw new IllegalStateException("Service " + dto.getName() + " must be linked to a deployment");
        }

        ContainerDTO containerDTO = deploymentDTO.getTargetNodes() != null
                ? deploymentDTO.getTargetNodes().stream()
                .filter(ContainerDTO.class::isInstance)
                .map(ContainerDTO.class::cast)
                .findFirst()
                .orElse(null)
                : null;

        Map<String, String> selectors = Collections.singletonMap("app", deploymentDTO.getName());

        ServicePort servicePort = new ServicePort();
        servicePort.setPort(dto.getPort());
        int targetPort = containerDTO != null
                ? containerDTO.getContainerPort()
                : (deploymentDTO.getContainerPort() != null ? deploymentDTO.getContainerPort() : dto.getPort());
        servicePort.setTargetPort(new IntOrString(targetPort));

        if ("NodePort".equals(dto.getType()) && dto.getNodePort() != null) {
            servicePort.setNodePort(dto.getNodePort());
        }

        io.fabric8.kubernetes.api.model.Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withSelector(selectors)
                .withType(dto.getType())
                .withPorts(servicePort)
                .endSpec()
                .build();

        return Serialization.asYaml(service);
    }

    private String createGateway(ServiceDTO dto, String namespace) {
        Gateway gateway = new GatewayBuilder()
                .withNewMetadata()
                .withName(dto.getName() + "-gateway")
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withSelector(Collections.singletonMap("istio", "ingressgateway"))
                .addNewServer()
                .withHosts(stripHttpPrefix(dto.getFrontendUrl()))
                .withNewPort()
                .withNumber(80)
                .withName("http")
                .withProtocol("HTTP")
                .endPort()
                .endServer()
                .endSpec()
                .build();

        return Serialization.asYaml(gateway);
    }

    private String createVirtualService(ServiceDTO dto, String namespace) {
        // Create URI match
        StringMatch uriMatch = new StringMatch();
        uriMatch.setAdditionalProperty("prefix", "/");
        HTTPMatchRequest matchRequest = new HTTPMatchRequest();
        matchRequest.setUri(uriMatch);

        // Create destination
        HTTPRouteDestination destination = new HTTPRouteDestination();
        Destination dest = new Destination();
        dest.setHost(dto.getName());
        dest.setPort(new PortSelector());
        dest.getPort().setNumber(dto.getPort());
        destination.setDestination(dest);

        // Create HTTP route and set headers
        HTTPRoute httpRoute = new HTTPRoute();
        httpRoute.setMatch(Collections.singletonList(matchRequest));
        httpRoute.setRoute(Collections.singletonList(destination));

        // Create VirtualService
        VirtualServiceBuilder virtualService = new VirtualServiceBuilder()
                .withNewMetadata()
                .withName(dto.getName() + "-virtualservice")
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withHttp(Collections.singletonList(httpRoute))
                .endSpec();

        if (dto.isExposeService() && !dto.getFrontendUrl().isEmpty()) {
            virtualService.editOrNewSpec()
                    .withHosts(Collections.singletonList(stripHttpPrefix(dto.getFrontendUrl())))
                    .withGateways(Collections.singletonList(dto.getName() + "-gateway"))
                    .endSpec();
        }
        return Serialization.asYaml(virtualService.build());
    }

    private String stripHttpPrefix(String url) {
        return url.replaceAll("^(http://|https://)", "");
    }

    private String resolveNamespace(ServiceDTO dto) {
        return dto.getNamespace() == null || dto.getNamespace().isBlank() ? "default" : dto.getNamespace();
    }

}
