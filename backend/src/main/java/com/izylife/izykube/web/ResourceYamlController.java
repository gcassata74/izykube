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

package com.izylife.izykube.web;

import com.izylife.izykube.services.KubernetesExplorerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kube/resources")
public class ResourceYamlController {

    private final KubernetesExplorerService explorerService;

    @GetMapping(value = "/{kind}/{namespace}/{name}/yaml", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getResourceYaml(@PathVariable String kind,
                                                  @PathVariable String namespace,
                                                  @PathVariable String name) {
        String yaml = explorerService.getResourceYaml(kind, namespace, name);
        if (yaml == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(yaml);
    }

    @PutMapping(value = "/{kind}/{namespace}/{name}/yaml", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> updateResourceYaml(@PathVariable String kind,
                                                     @PathVariable String namespace,
                                                     @PathVariable String name,
                                                     @RequestBody String yaml) {
        String updated = explorerService.applyResourceYaml(kind, namespace, name, yaml);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(updated);
    }
}
