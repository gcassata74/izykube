/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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

@Processor(ServiceDTO.class)
@Service
public class ServiceProcessor implements TemplateProcessor<ServiceDTO> {

    @Override
    public String createTemplate(ServiceDTO dto) {
        StringBuilder yaml = new StringBuilder();
        String namespace = resolveNamespace(dto);
        String serviceName = sanitizeName(dto.getName());
        if (serviceName.isEmpty()) {
            throw new IllegalStateException("Service name is required to generate templates");
        }

        // Create Kubernetes Service
        yaml.append(createKubernetesService(dto, namespace, serviceName));

        // Always create VirtualService
        yaml.append(createVirtualService(dto, namespace, serviceName));

        // If service is exposed, create Gateway
        if (dto.isExposeService() && dto.getFrontendUrl() != null && !dto.getFrontendUrl().isEmpty()) {
            yaml.append(createGateway(dto, namespace, serviceName));
        }

        return yaml.toString();
    }

    private String createKubernetesService(ServiceDTO dto, String namespace, String serviceName) {
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

        ServicePort servicePort = new ServicePort();
        servicePort.setPort(dto.getPort());
        int targetPort = containerDTO != null
                ? containerDTO.getContainerPort()
                : (deploymentDTO.getContainerPort() != null ? deploymentDTO.getContainerPort() : dto.getPort());
        servicePort.setTargetPort(new IntOrString(targetPort));

        String selectorValue = sanitizeName(deploymentDTO.getName());
        if (selectorValue.isEmpty()) {
            throw new IllegalStateException("Deployment name is required to build service selectors");
        }
        if ("NodePort".equals(dto.getType()) && dto.getNodePort() != null) {
            servicePort.setNodePort(dto.getNodePort());
        }

        io.fabric8.kubernetes.api.model.Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(serviceName)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withSelector(Collections.singletonMap("app", selectorValue))
                .withType(dto.getType())
                .withPorts(servicePort)
                .endSpec()
                .build();

        return Serialization.asYaml(service);
    }

    private String createGateway(ServiceDTO dto, String namespace, String serviceName) {
        Gateway gateway = new GatewayBuilder()
                .withNewMetadata()
                .withName(serviceName + "-gateway")
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

    private String createVirtualService(ServiceDTO dto, String namespace, String serviceName) {
        // Create URI match
        StringMatch uriMatch = new StringMatch();
        uriMatch.setAdditionalProperty("prefix", "/");
        HTTPMatchRequest matchRequest = new HTTPMatchRequest();
        matchRequest.setUri(uriMatch);

        // Create destination
        HTTPRouteDestination destination = new HTTPRouteDestination();
        Destination dest = new Destination();
        dest.setHost(serviceName);
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
                .withName(serviceName + "-virtualservice")
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withHttp(Collections.singletonList(httpRoute))
                .endSpec();

        if (dto.isExposeService() && !dto.getFrontendUrl().isEmpty()) {
            virtualService.editOrNewSpec()
                    .withHosts(Collections.singletonList(stripHttpPrefix(dto.getFrontendUrl())))
                    .withGateways(Collections.singletonList(serviceName + "-gateway"))
                    .endSpec();
        }
        return Serialization.asYaml(virtualService.build());
    }

    private String stripHttpPrefix(String url) {
        return url.replaceAll("^(http://|https://)", "");
    }

    private String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return normalized;
    }

    private String resolveNamespace(ServiceDTO dto) {
        return dto.getNamespace() == null || dto.getNamespace().isBlank() ? "default" : dto.getNamespace();
    }

}
