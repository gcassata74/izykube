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

import com.izylife.izykube.dto.cluster.ConfigEntryDTO;
import com.izylife.izykube.dto.cluster.ConfigEntrySensitivity;
import com.izylife.izykube.dto.cluster.SecretDTO;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Processor(SecretDTO.class)
@Service
public class SecretProcessor implements TemplateProcessor<SecretDTO> {

    @Override
    public String createTemplate(SecretDTO dto) {
        String namespace = dto.getNamespace() == null || dto.getNamespace().isBlank() ? "default" : dto.getNamespace();
        String secretName = sanitizeName(dto.getName());
        if (secretName.isEmpty()) {
            throw new IllegalArgumentException("Secret name is required to generate manifests");
        }

        Map<String, String> decoded = decodeIfNeeded(YamlKeyValueExtractor.extractPlainKeyValueData(dto.getYaml()));
        if (decoded.isEmpty()) {
            decoded = buildValuesFromEntries(dto.getEntries());
        }
        Map<String, String> encoded = encodeSecretData(decoded);
        Map<String, String> dataSection = new LinkedHashMap<>(encoded);

        return Serialization.asYaml(
                new SecretBuilder()
                        .withNewMetadata()
                        .withName(secretName)
                        .withNamespace(namespace)
                        .endMetadata()
                        .withType("Opaque")
                        .withData(dataSection)
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
        values.forEach((key, value) -> {
            String sanitizedKey = sanitizeKey(key);
            if (!sanitizedKey.isEmpty()) {
                decoded.put(sanitizedKey, decodeValue(value));
            }
        });
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

    private Map<String, String> buildValuesFromEntries(List<ConfigEntryDTO> entries) {
        Map<String, String> values = new LinkedHashMap<>();
        if (entries == null) {
            return values;
        }
        for (ConfigEntryDTO entry : entries) {
            String sanitizedKey = sanitizeKey(entry == null ? null : entry.getKey());
            if (sanitizedKey.isEmpty()) {
                continue;
            }
            if (!ConfigEntrySensitivity.SECRET.equals(entry.getSensitivity())) {
                continue;
            }
            values.put(sanitizedKey, entry.getValue() == null ? "" : entry.getValue());
        }
        return values;
    }

    private String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return normalized;
    }

    private String sanitizeKey(String key) {
        return key == null ? "" : key.trim();
    }
}
