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
import java.util.HashMap;
import java.util.Map;

@Processor(ServiceDTO.class)
@Service
public class ServiceProcessor implements TemplateProcessor<ServiceDTO> {

    @Override
    public String createTemplate(ServiceDTO dto) {
        StringBuilder yaml = new StringBuilder();

        // Create Kubernetes Service
        yaml.append(createKubernetesService(dto));

        // Always create VirtualService
        yaml.append("---\n");
        yaml.append(createVirtualService(dto));

        // If service is exposed, create Gateway
        if (dto.isExposeService() && dto.getFrontendUrl() != null && !dto.getFrontendUrl().isEmpty()) {
            yaml.append("---\n");
            yaml.append(createGateway(dto));
        }

        return yaml.toString();
    }

    private String createKubernetesService(ServiceDTO dto) {
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

        Map<String, String> selectors = Collections.singletonMap("app", deploymentDTO.getName());

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

    private String createGateway(ServiceDTO dto) {
        Gateway gateway = new GatewayBuilder()
                .withNewMetadata()
                .withName(dto.getName() + "-gateway")
                .withNamespace("default")
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

    private String createVirtualService(ServiceDTO dto) {
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

        Map<String, Object> headers = new HashMap<>();
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("x-forwarded-host", stripHttpPrefix(dto.getFrontendUrl()));
        headers.put("request", Collections.singletonMap("set", requestHeaders));

        // Create HTTP route and set headers
        HTTPRoute httpRoute = new HTTPRoute();
        httpRoute.setMatch(Collections.singletonList(matchRequest));
        httpRoute.setRoute(Collections.singletonList(destination));
        httpRoute.setAdditionalProperty("headers", headers);


        // Create VirtualService
        VirtualService virtualService = new VirtualServiceBuilder()
                .withNewMetadata()
                .withName(dto.getName() + "-virtualservice")
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withHosts(Collections.singletonList(stripHttpPrefix(dto.getFrontendUrl())))
                .withGateways(Collections.singletonList(dto.getName() + "-gateway"))
                .withHttp(Collections.singletonList(httpRoute))
                .endSpec()
                .build();

        return Serialization.asYaml(virtualService);
    }

    private String stripHttpPrefix(String url) {
        return url.replaceAll("^(http://|https://)", "");
    }


}