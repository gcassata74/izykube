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

import com.izylife.izykube.dto.cluster.CustomResourceDTO;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

@Processor(CustomResourceDTO.class)
@Service
public class CustomResourceProcessor implements TemplateProcessor<CustomResourceDTO> {

    @Override
    public String createTemplate(CustomResourceDTO dto) {
        String group = trim(dto.getCrdGroup());
        String version = trim(dto.getCrdVersion());
        String kind = trim(dto.getCrdKind());
        String name = trim(dto.getName());
        if (group.isEmpty() || version.isEmpty() || kind.isEmpty()) {
            throw new IllegalArgumentException("Custom Resource requires CRD group, version and kind.");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Custom Resource name is required.");
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", group + "/" + version);
        manifest.put("kind", kind);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", name);
        boolean namespaced = !"cluster".equalsIgnoreCase(trim(dto.getCrdScope()));
        if (namespaced) {
            String namespace = dto.getNamespace() == null || dto.getNamespace().isBlank() ? "default" : dto.getNamespace().trim();
            metadata.put("namespace", namespace);
        }
        manifest.put("metadata", metadata);
        manifest.put("spec", dto.getSpec() == null ? new LinkedHashMap<>() : dto.getSpec());

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(manifest);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
