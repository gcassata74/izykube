package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.IngressDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import io.fabric8.kubernetes.api.model.networking.v1.*;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Processor(IngressDTO.class)
@Service
public class IngressProcessor implements TemplateProcessor<IngressDTO> {

    @Override
    public String createTemplate(IngressDTO dto) {
        List<IngressRule> rules = new ArrayList<>();

        List<ServiceDTO> services = dto.getSourceNodes().stream()
                .filter(ServiceDTO.class::isInstance)
                .map(ServiceDTO.class::cast)
                .collect(Collectors.toList());

        if (services.isEmpty() && dto.getServiceName() != null && !dto.getServiceName().isBlank()) {
            int port = dto.getServicePort() > 0 ? dto.getServicePort() : 80;
            services = Collections.singletonList(new ServiceDTO(dto.getServiceName(), dto.getServiceName(), "ClusterIP", port));
        }

        if (services.isEmpty()) {
            throw new IllegalStateException("Ingress must be connected to at least one service or define serviceName/servicePort");
        }

        if (dto.getServiceName() != null && !dto.getServiceName().isBlank()) {
            final String desiredService = dto.getServiceName();
            List<ServiceDTO> filtered = services.stream()
                    .filter(service -> service.getName().equals(desiredService))
                    .collect(Collectors.toList());
            if (!filtered.isEmpty()) {
                services = filtered;
            }
        }

        String basePath = normalizePath(dto.getPath());
        boolean appendServiceName = services.size() > 1 || dto.getServiceName() == null || dto.getServiceName().isBlank();
        Set<String> tlsHosts = new LinkedHashSet<>();

        for (ServiceDTO serviceDTO : services) {
            String path = appendServiceName ? appendServiceName(basePath, serviceDTO.getName()) : basePath;
            int port = dto.getServicePort() > 0 ? dto.getServicePort() : serviceDTO.getPort();

            HTTPIngressPath httpIngressPath = new HTTPIngressPathBuilder()
                    .withPath(path)
                    .withPathType("Prefix")
                    .withBackend(new IngressBackendBuilder()
                            .withService(new IngressServiceBackendBuilder()
                                    .withName(serviceDTO.getName())
                                    .withPort(new ServiceBackendPortBuilder()
                                            .withNumber(port)
                                            .build())
                                    .build())
                            .build())
                    .build();

            String host = resolveHost(dto, serviceDTO);
            if (host != null && !host.isBlank()) {
                tlsHosts.add(host);
            }

            IngressRule ingressRule = new IngressRuleBuilder()
                    .withHost(host)
                    .withHttp(new HTTPIngressRuleValueBuilder()
                            .withPaths(httpIngressPath)
                            .build())
                    .build();

            rules.add(ingressRule);
        }

        List<IngressTLS> tlsEntries = new ArrayList<>();
        if (dto.getTls() != null && !dto.getTls().isBlank()) {
            tlsEntries.add(new IngressTLSBuilder()
                    .withSecretName(dto.getTls())
                    .withHosts(tlsHosts.isEmpty() ? null : new ArrayList<>(tlsHosts))
                    .build());
        }

        Ingress ingress = new IngressBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace("default")
                .withAnnotations(normalizeAnnotations(dto.getAnnotations()))
                .endMetadata()
                .withNewSpec()
                .withRules(rules)
                .withTls(tlsEntries.isEmpty() ? null : tlsEntries)
                .endSpec()
                .build();

        return Serialization.asYaml(ingress);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String appendServiceName(String basePath, String serviceName) {
        String normalized = basePath.endsWith("/") ? basePath : basePath + "/";
        return normalized + serviceName;
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

    private Map<String, String> normalizeAnnotations(Map<String, String> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return null;
        }
        return annotations.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() != null ? entry.getValue() : "",
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
    }
}
