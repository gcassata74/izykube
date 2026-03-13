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

import com.izylife.izykube.dto.cluster.IngressDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Processor(IngressDTO.class)
@Service
public class IngressProcessor implements TemplateProcessor<IngressDTO> {

    @Override
    public String createTemplate(IngressDTO dto) {
        String namespace = dto.getNamespace() == null || dto.getNamespace().isBlank() ? "default" : dto.getNamespace();

        List<ServiceDTO> services = dto.getSourceNodes().stream()
                .filter(ServiceDTO.class::isInstance)
                .map(ServiceDTO.class::cast)
                .collect(Collectors.toList());

        if (services.isEmpty() && dto.getServiceName() != null && !dto.getServiceName().isBlank()) {
            int port = dto.getServicePort() > 0 ? dto.getServicePort() : 80;
            services = Collections.singletonList(new ServiceDTO(dto.getServiceName(), dto.getServiceName(), "ClusterIP", port));
        }

        if (services.isEmpty()) {
            throw new IllegalStateException("Route must be connected to at least one service or define serviceName/servicePort");
        }

        ServiceDTO service = services.get(0);
        if (dto.getServiceName() != null && !dto.getServiceName().isBlank()) {
            String desiredService = dto.getServiceName();
            service = services.stream()
                    .filter(entry -> entry.getName().equals(desiredService))
                    .findFirst()
                    .orElse(service);
        }

        String host = resolveHost(dto, service);
        if (host == null || host.isBlank()) {
            host = "*";
        }
        String path = normalizePath(dto.getPath());
        int port = dto.getServicePort() > 0 ? dto.getServicePort() : service.getPort();
        String gatewayName = dto.getName() + "-gateway";

        String tlsBlock = "";
        if (dto.getTls() != null && !dto.getTls().isBlank()) {
            tlsBlock = """
                  - port:
                      number: 443
                      name: https
                      protocol: HTTPS
                    tls:
                      mode: SIMPLE
                      credentialName: %s
                    hosts:
                      - %s
                """.formatted(dto.getTls(), host);
        }

        return """
                apiVersion: networking.istio.io/v1beta1
                kind: Gateway
                metadata:
                  name: %s
                  namespace: %s
                spec:
                  selector:
                    istio: ingressgateway
                  servers:
                    - port:
                        number: 80
                        name: http
                        protocol: HTTP
                      hosts:
                        - %s
                %s
                ---
                apiVersion: networking.istio.io/v1beta1
                kind: VirtualService
                metadata:
                  name: %s
                  namespace: %s
                spec:
                  hosts:
                    - %s
                  gateways:
                    - %s
                  http:
                    - match:
                        - uri:
                            prefix: %s
                      route:
                        - destination:
                            host: %s
                            port:
                              number: %d
                """.formatted(
                gatewayName,
                namespace,
                host,
                tlsBlock.isBlank() ? "" : tlsBlock.indent(2),
                dto.getName(),
                namespace,
                host,
                gatewayName,
                path,
                service.getName(),
                port
        );
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String resolveHost(IngressDTO dto, ServiceDTO serviceDTO) {
        if (dto.getHost() != null && !dto.getHost().isBlank()) {
            return dto.getHost();
        }

        if (serviceDTO.getFrontendUrl() != null && !serviceDTO.getFrontendUrl().isBlank()) {
            return stripHttpPrefix(serviceDTO.getFrontendUrl());
        }

        return "example.com";
    }

    private String stripHttpPrefix(String url) {
        return url.replaceAll("^(http://|https://)", "");
    }

}
