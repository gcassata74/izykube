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
import com.izylife.izykube.dto.cluster.ConfigMapDTO;
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
        List<ConfigEntryDTO> entries = dto.getEntries();
        boolean hasEntries = entries != null && !entries.isEmpty();
        String output = "";

        if (hasEntries) {
            Map<String, String> plainEntries = buildValuesFromEntries(entries, false);
            Map<String, String> secretEntries = buildValuesFromEntries(entries, true);

            StringBuilder yamlBuilder = new StringBuilder();
            if (!plainEntries.isEmpty()) {
                yamlBuilder.append(
                        Serialization.asYaml(
                                new ConfigMapBuilder()
                                        .withNewMetadata()
                                        .withName(dto.getName())
                                        .withNamespace(namespace)
                                        .endMetadata()
                                        .withData(plainEntries)
                                        .build()
                        )
                );
            }

            if (!secretEntries.isEmpty()) {
                if (!yamlBuilder.isEmpty()) {
                    yamlBuilder.append("---\n");
                }
                Map<String, String> encoded = encodeSecretData(secretEntries);
                yamlBuilder.append(
                        Serialization.asYaml(
                                new SecretBuilder()
                                        .withNewMetadata()
                                        .withName(dto.getName())
                                        .withNamespace(namespace)
                                        .endMetadata()
                                        .withType("Opaque")
                                        .withData(encoded)
                                        .build()
                        )
                );
            }
            if (!yamlBuilder.isEmpty()) {
                output = yamlBuilder.toString();
            }
        }
        return output;
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
