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

package com.izylife.izykube.services.ai;

import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.dto.cluster.ContainerRole;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.DeploymentWorkloadType;
import com.izylife.izykube.dto.cluster.LinkDTO;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClusterYamlServiceContainerRolesTest {

    @Test
    void initContainerLinkedToStatefulSetIsExportedUnderInitContainers() {
        ClusterDTO cluster = new ClusterDTO();
        cluster.setName("test");
        cluster.setNameSpace("test-image");

        DeploymentDTO stateful = new DeploymentDTO("dep-1", "deployment", 1, "RollingUpdate", "asset-main", 3306, DeploymentWorkloadType.STATEFULSET);
        ContainerDTO init = new ContainerDTO("c-init", "container", "asset-init", 80, ContainerRole.INIT);
        cluster.setNodes(List.of(stateful, init));

        LinkDTO link = new LinkDTO();
        link.setSource(stateful.getId());
        link.setTarget(init.getId());
        link.setType("Expose"); // legacy diagrams used Expose for container attachment
        cluster.setLinks(List.of(link));

        cluster.setDiagram("{\"nodes\":[],\"links\":[],\"rawManifests\":[]}");

        String yamlOut = new ClusterYamlService().exportCluster(cluster);
        Map<String, Object> statefulDoc = firstDocOfKind(yamlOut, "StatefulSet");
        assertNotNull(statefulDoc);

        Map<String, Object> spec = getMap(statefulDoc, "spec");
        Map<String, Object> template = getMap(spec, "template");
        Map<String, Object> podSpec = getMap(template, "spec");
        List<Map<String, Object>> initContainers = getList(podSpec, "initContainers");
        assertNotNull(initContainers);
        assertEquals(1, initContainers.size());
        assertEquals("container", initContainers.get(0).get("name"));
    }

    @Test
    void missingRoleDefaultsToSidecarAndAppendsAfterMain() {
        ClusterDTO cluster = new ClusterDTO();
        cluster.setName("test");
        cluster.setNameSpace("ns");

        DeploymentDTO deployment = new DeploymentDTO("dep-2", "app", 1, "RollingUpdate", "asset-main", 8080, DeploymentWorkloadType.DEPLOYMENT);
        ContainerDTO sidecar = new ContainerDTO("c-side", "alpha", "asset-side", 80, null);
        cluster.setNodes(List.of(deployment, sidecar));

        LinkDTO link = new LinkDTO();
        link.setSource(deployment.getId());
        link.setTarget(sidecar.getId());
        link.setType("Container");
        cluster.setLinks(List.of(link));
        cluster.setDiagram("{\"nodes\":[],\"links\":[],\"rawManifests\":[]}");

        String yamlOut = new ClusterYamlService().exportCluster(cluster);
        Map<String, Object> depDoc = firstDocOfKind(yamlOut, "Deployment");
        Map<String, Object> spec = getMap(depDoc, "spec");
        Map<String, Object> template = getMap(spec, "template");
        Map<String, Object> podSpec = getMap(template, "spec");
        List<Map<String, Object>> containers = getList(podSpec, "containers");
        assertNotNull(containers);
        assertEquals(List.of("app", "alpha"), containers.stream().map(c -> (String) c.get("name")).toList());
        assertTrue(getList(podSpec, "initContainers") == null || getList(podSpec, "initContainers").isEmpty());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstDocOfKind(String yaml, String kind) {
        Yaml parser = new Yaml();
        for (Object doc : parser.loadAll(yaml)) {
            if (!(doc instanceof Map<?, ?> map)) {
                continue;
            }
            Object docKind = map.get("kind");
            if (kind.equals(docKind)) {
                return (Map<String, Object>) map;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        assertTrue(value instanceof Map, "Expected map for key " + key);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value == null) {
            return null;
        }
        assertTrue(value instanceof List, "Expected list for key " + key);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object item : (List<?>) value) {
            assertTrue(item instanceof Map, "Expected map items in list " + key);
            list.add((Map<String, Object>) item);
        }
        return list;
    }
}

