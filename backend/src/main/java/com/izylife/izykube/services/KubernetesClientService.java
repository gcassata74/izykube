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

package com.izylife.izykube.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.izykube.factory.KubernetesResourceFactory;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KubernetesClientService {
    private final KubernetesResourceFactory resourceFactory;
    private final KubernetesClient kubernetesClient;
    private final ObjectMapper objectMapper;


    public KubernetesClientService(KubernetesClient kubernetesClient, ObjectMapper objectMapper, KubernetesResourceFactory resourceFactory) {
        this.resourceFactory = resourceFactory;
        this.kubernetesClient = kubernetesClient;
        this.objectMapper = objectMapper;
    }

    public HasMetadata createOrUpdate(String json, String namespace) throws Exception {
        if (StringUtils.isEmpty(namespace)) {
            namespace = "default";
        }

        GenericKubernetesResource resource = resourceFactory.createGenericResource(json);
        return kubernetesClient.resource(resource).inNamespace(namespace).createOrReplace();
    }

    public void delete(String json, String namespace) throws Exception {
        if (StringUtils.isEmpty(namespace)) {
            namespace = "default";
        }

        GenericKubernetesResource resource = resourceFactory.createGenericResource(json);
        kubernetesClient.resource(resource).inNamespace(namespace).delete();
    }


    public Object getAll(String kind, String namespace) {
        if (StringUtils.isEmpty(namespace)) {
            namespace = "default";
        }

        Class<? extends HasMetadata> resourceClass = resourceFactory.getResourceClass(kind);
        return kubernetesClient.resources(resourceClass).inNamespace(namespace).list();
    }
}

