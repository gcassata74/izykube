package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ServiceDTO;
import com.izylife.izykube.dto.cluster.VirtualServiceDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Processor(VirtualServiceDTO.class)
@Service
public class VirtualServiceProcessor implements TemplateProcessor<VirtualServiceDTO> {

    @Override
    public String createTemplate(VirtualServiceDTO dto) {
        List<ServiceDTO> services = dto.getSourceNodes().stream()
                .filter(ServiceDTO.class::isInstance)
                .map(ServiceDTO.class::cast)
                .collect(Collectors.toList());

        if (services.isEmpty() && dto.getServiceName() != null && !dto.getServiceName().isBlank()) {
            int port = dto.getServicePort() > 0 ? dto.getServicePort() : 80;
            services = Collections.singletonList(new ServiceDTO(dto.getServiceName(), dto.getServiceName(), "ClusterIP", port));
        }

        if (services.isEmpty()) {
            throw new IllegalStateException("VirtualService must be connected to at least one service or define serviceName/servicePort");
        }

        List<String> routeEntries = new ArrayList<>();
        for (ServiceDTO serviceDTO : services) {
            int port = dto.getServicePort() > 0 ? dto.getServicePort() : serviceDTO.getPort();
            routeEntries.add("""
                    - route:
                        - destination:
                            host: %s
                            port:
                              number: %d
                    """.formatted(serviceDTO.getName(), port));
        }

        return """
                apiVersion: networking.istio.io/v1beta1
                kind: VirtualService
                metadata:
                  name: %s
                spec:
                  hosts:
                    - %s
                  http:
                %s
                """.formatted(
                dto.getName(),
                dto.getHost() != null && !dto.getHost().isBlank() ? dto.getHost() : "example.com",
                routeEntries.stream().collect(Collectors.joining("\n")).indent(4)
        );
    }
}
