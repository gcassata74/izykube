package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ConfigEntryDTO;
import com.izylife.izykube.dto.cluster.ConfigEntrySensitivity;
import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Processor(ConfigMapDTO.class)
@Service
public class ConfigMapProcessor implements TemplateProcessor<ConfigMapDTO> {

    @Override
    public String createTemplate(ConfigMapDTO dto) {
        String namespace = dto.getNamespace() == null || dto.getNamespace().isBlank() ? "default" : dto.getNamespace();
        boolean secretResource = dto.isSecret() || containsSecretEntry(dto.getEntries());

        Map<String, String> data = YamlKeyValueExtractor.extractPlainKeyValueData(dto.getYaml());
        if (data.isEmpty()) {
            data = buildValuesFromEntries(dto.getEntries(), secretResource);
        }

        if (secretResource) {
            Map<String, String> encoded = encodeSecretData(data);
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

        if (data.isEmpty()) {
            return "";
        }

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace(namespace)
                .endMetadata()
                .withData(data)
                .build();

        return Serialization.asYaml(configMap);
    }

    private Map<String, String> buildValuesFromEntries(List<ConfigEntryDTO> entries, boolean secret) {
        Map<String, String> values = new LinkedHashMap<>();
        if (entries == null) {
            return values;
        }
        for (ConfigEntryDTO entry : entries) {
            if (entry == null || entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            boolean isSecretEntry = ConfigEntrySensitivity.SECRET.equals(entry.getSensitivity());
            if (secret && !isSecretEntry) {
                continue;
            }
            if (!secret && isSecretEntry) {
                continue;
            }
            values.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return values;
    }

    private boolean containsSecretEntry(List<ConfigEntryDTO> entries) {
        if (entries == null) {
            return false;
        }
        return entries.stream().anyMatch(entry -> ConfigEntrySensitivity.SECRET.equals(entry.getSensitivity()));
    }

    private Map<String, String> encodeSecretData(Map<String, String> decoded) {
        Map<String, String> encoded = new LinkedHashMap<>();
        if (decoded == null) {
            return encoded;
        }
        decoded.forEach((key, value) ->
                encoded.put(key, Base64.getEncoder().encodeToString(
                        value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8)
                )));
        return encoded;
    }

}
