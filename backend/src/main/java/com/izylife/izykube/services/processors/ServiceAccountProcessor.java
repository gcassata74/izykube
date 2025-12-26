package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ServiceAccountDTO;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Processor(ServiceAccountDTO.class)
@Service
public class ServiceAccountProcessor implements TemplateProcessor<ServiceAccountDTO> {

    @Override
    public String createTemplate(ServiceAccountDTO dto) {
        String namespace = resolveNamespace(dto);
        String rawName = dto != null ? dto.getName() : null;
        String name = normalizeName(rawName);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ServiceAccount name is required to generate templates");
        }
        validateDns1123Subdomain(name);

        Map<String, String> labels = sanitizeStringMap(dto != null ? dto.getLabels() : null);
        Map<String, String> annotations = sanitizeStringMap(dto != null ? dto.getAnnotations() : null);
        Boolean automount = dto != null ? dto.getAutomountServiceAccountToken() : null;

        ServiceAccountBuilder builder = new ServiceAccountBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(labels.isEmpty() ? null : labels)
                .withAnnotations(annotations.isEmpty() ? null : annotations)
                .endMetadata()
                .withAutomountServiceAccountToken(Optional.ofNullable(automount).orElse(true));

        return Serialization.asYaml(builder.build());
    }

    private String resolveNamespace(ServiceAccountDTO dto) {
        String ns = dto != null ? dto.getNamespace() : null;
        return ns == null || ns.isBlank() ? "default" : ns;
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }

    private void validateDns1123Subdomain(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ServiceAccount name is required");
        }
        if (name.length() > 253) {
            throw new IllegalArgumentException("ServiceAccount name must be <= 253 characters");
        }
        if (!name.matches("^[a-z0-9]([a-z0-9-.]*[a-z0-9])?$")) {
            throw new IllegalArgumentException("ServiceAccount name must be a valid DNS-1123 subdomain (lowercase alphanumeric, '-', '.', start/end alphanumeric)");
        }
    }

    private Map<String, String> sanitizeStringMap(Map<String, String> input) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        if (input == null) {
            return sanitized;
        }
        input.forEach((key, value) -> {
            String normalizedKey = key == null ? "" : key.trim();
            if (normalizedKey.isEmpty()) {
                return;
            }
            sanitized.put(normalizedKey, value == null ? "" : value);
        });
        return sanitized;
    }
}
