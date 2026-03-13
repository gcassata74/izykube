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

import com.izylife.izykube.services.OperatorHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/operatorhub")
public class OperatorHubController {

    private final OperatorHubService operatorHubService;

    @GetMapping("/operators")
    public ResponseEntity<?> listOperators(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            return ResponseEntity.ok(operatorHubService.listOperators(q, page, size));
        } catch (Exception e) {
            log.error("Error listing OperatorHub operators: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error listing OperatorHub operators: " + e.getMessage());
        }
    }

    @GetMapping(value = "/install/{name}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> getInstallYaml(@PathVariable String name) {
        try {
            return ResponseEntity.ok(operatorHubService.getInstallYaml(name));
        } catch (Exception e) {
            log.error("Error fetching OperatorHub install YAML {}: {}", name, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error fetching OperatorHub install YAML: " + e.getMessage());
        }
    }
}
