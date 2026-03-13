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

package com.izylife.izykube.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class KubernetesResourceFactory {

    private final ObjectMapper objectMapper;

    public KubernetesResourceFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Class<? extends HasMetadata> getResourceClass(String kind) {
        switch (kind.toLowerCase()) {
            case "pod":
                return Pod.class;
            case "service":
                return Service.class;
            case "deployment":
                return Deployment.class;
            // Add more kinds as needed...
            default:
                throw new IllegalArgumentException("Unknown kind: " + kind);
        }
    }

    public GenericKubernetesResource createGenericResource(String json) throws IOException {
        return objectMapper.readValue(json, GenericKubernetesResource.class);
    }
}
