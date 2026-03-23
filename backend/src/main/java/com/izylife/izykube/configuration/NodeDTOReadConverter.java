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

package com.izylife.izykube.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.izykube.dto.cluster.NodeDTO;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * Tells Spring Data MongoDB how to deserialize the abstract NodeDTO type.
 *
 * MappingMongoConverter does not use Jackson's @JsonTypeInfo/@JsonSubTypes —
 * it relies on its own reflection-based mapping and will crash trying to
 * instantiate the abstract NodeDTO class directly.
 *
 * This converter delegates to Jackson (which already has the full @JsonSubTypes
 * mapping configured on NodeDTO) so polymorphic node documents are correctly
 * hydrated into PodDTO, DeploymentDTO, ServiceDTO, etc.
 */
@ReadingConverter
public class NodeDTOReadConverter implements Converter<Document, NodeDTO> {

    private final ObjectMapper objectMapper;

    public NodeDTOReadConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public NodeDTO convert(Document source) {
        try {
            // Normalize the "kind" discriminator to lowercase so that documents
            // written with PascalCase values (e.g. "Pod", "Deployment") match the
            // lowercase @JsonSubTypes names ("pod", "deployment") on NodeDTO.
            Document normalized = new Document(source);
            if (normalized.containsKey("kind") && normalized.get("kind") instanceof String) {
                normalized.put("kind", ((String) normalized.get("kind")).toLowerCase());
            }
            return objectMapper.readValue(normalized.toJson(), NodeDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to deserialize NodeDTO from document: " + source.toJson(), e);
        }
    }
}
