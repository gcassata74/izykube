package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.SecretDTO;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Processor(SecretDTO.class)
@Service
public class SecretProcessor implements TemplateProcessor<SecretDTO> {

    @Override
    public String createTemplate(SecretDTO dto) {
        String namespace = dto.getNamespace() == null || dto.getNamespace().isBlank() ? "default" : dto.getNamespace();
        Map<String, String> decoded = decodeIfNeeded(YamlKeyValueExtractor.extractPlainKeyValueData(dto.getYaml()));
        Map<String, String> encoded = encodeSecretData(decoded);

        return Serialization.asYaml(
                new SecretBuilder()
                        .withNewMetadata()
                        .withName(dto.getName())
                        .withNamespace(namespace)
                        .endMetadata()
                        .withType("Opaque")
                        .withData(encoded)
                        .build()
        );
    }

    private Map<String, String> encodeSecretData(Map<String, String> decoded) {
        Map<String, String> encoded = new LinkedHashMap<>();
        decoded.forEach((key, value) ->
                encoded.put(key, Base64.getEncoder().encodeToString(
                        value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8)
                )));
        return encoded;
    }

    private Map<String, String> decodeIfNeeded(Map<String, String> values) {
        Map<String, String> decoded = new LinkedHashMap<>();
        values.forEach((key, value) -> decoded.put(key, decodeValue(value)));
        return decoded;
    }

    private String decodeValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }
}
