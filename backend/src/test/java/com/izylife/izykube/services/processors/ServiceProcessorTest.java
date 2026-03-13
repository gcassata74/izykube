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

import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.DeploymentWorkloadType;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServiceProcessorTest {

    private final ServiceProcessor processor = new ServiceProcessor();

    @Test
    void trimsWhitespaceFromNamesAndSelectors() {
        ServiceDTO service = new ServiceDTO("service:mysql", "mysql-service  ", "ClusterIP", 3306);
        DeploymentDTO deployment = new DeploymentDTO("deployment:mysql", "deployment    ", 1, "RollingUpdate", "", 3306, DeploymentWorkloadType.DEPLOYMENT);
        service.setTargetNodes(List.of(deployment));
        deployment.setTargetNodes(new ArrayList<>());

        String template = processor.createTemplate(service);

        Map<String, Object> serviceManifest = null;
        Map<String, Object> virtualServiceManifest = null;
        for (Object doc : new Yaml().loadAll(template)) {
            if (!(doc instanceof Map<?, ?> manifest)) {
                continue;
            }
            String kind = Objects.toString(manifest.get("kind"), "");
            if ("Service".equals(kind)) {
                serviceManifest = castMap(manifest);
            } else if ("VirtualService".equals(kind)) {
                virtualServiceManifest = castMap(manifest);
            }
        }

        assertNotNull(serviceManifest, "Service manifest should be present");
        Map<String, Object> metadata = castMap(serviceManifest.get("metadata"));
        assertEquals("mysql-service", metadata.get("name"));
        Map<String, Object> spec = castMap(serviceManifest.get("spec"));
        Map<String, Object> selector = castMap(spec.get("selector"));
        assertEquals("deployment", selector.get("app"));

        assertNotNull(virtualServiceManifest, "VirtualService manifest should be present");
        Map<String, Object> vsMetadata = castMap(virtualServiceManifest.get("metadata"));
        assertEquals("mysql-service-virtualservice", vsMetadata.get("name"));
        Map<String, Object> vsSpec = castMap(virtualServiceManifest.get("spec"));
        List<Map<String, Object>> http = castList(vsSpec.get("http"));
        Map<String, Object> route = castMap(http.get(0));
        List<Map<String, Object>> destinations = castList(route.get("route"));
        Map<String, Object> destination = castMap(destinations.get(0).get("destination"));
        assertEquals("mysql-service", destination.get("host"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
