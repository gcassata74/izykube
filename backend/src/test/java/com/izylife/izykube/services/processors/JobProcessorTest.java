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

import com.izylife.izykube.dto.cluster.JobDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.ServiceAccountDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class JobProcessorTest {

    private AssetRepository assetRepository;
    private JobProcessor processor;

    @BeforeEach
    void setUp() {
        assetRepository = Mockito.mock(AssetRepository.class);
        when(assetRepository.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            Asset asset = new Asset();
            asset.setId(id);
            asset.setScript("#!/bin/sh\necho hello\n");
            asset.setImage("repo/" + id + ":1.0.0");
            return Optional.of(asset);
        });

        processor = new JobProcessor(assetRepository, new ContainerProcessor(assetRepository), new ConfigMapProcessor());
    }

    @Test
    void injectsServiceAccountNameWhenRefPresent() {
        JobDTO job = new JobDTO("job-1", "job-a", "asset-1", "sa-1");
        job.setNamespace("test-ns");
        job.setSourceNodes(List.of(new ServiceDTO("svc-1", "target-svc", "ClusterIP", 80)));

        ServiceAccountDTO sa = new ServiceAccountDTO("sa-1", "example-sa");
        sa.setNamespace("test-ns");
        job.setNodeIndex(Map.of(sa.getId(), (NodeDTO) sa));

        String yaml = processor.createTemplate(job);
        Iterable<Object> docs = new Yaml().loadAll(yaml);

        Map<String, Object> jobDoc = null;
        for (Object doc : docs) {
            if (!(doc instanceof Map<?, ?> map)) {
                continue;
            }
            if ("Job".equals(map.get("kind"))) {
                jobDoc = castMap(doc);
            }
        }

        assertNotNull(jobDoc);
        Map<String, Object> spec = castMap(jobDoc.get("spec"));
        Map<String, Object> template = castMap(spec.get("template"));
        Map<String, Object> podSpec = castMap(template.get("spec"));
        assertEquals("example-sa", podSpec.get("serviceAccountName"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        assertNotNull(value);
        return (Map<String, Object>) value;
    }
}
