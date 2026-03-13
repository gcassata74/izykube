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

import com.izylife.izykube.dto.operator.OperatorCatalogActionRequestDTO;
import com.izylife.izykube.dto.operator.OperatorCatalogRequestDTO;
import com.izylife.izykube.services.OperatorCatalogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/operator-catalog")
public class OperatorCatalogController {

    private final OperatorCatalogService operatorCatalogService;

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            return ResponseEntity.ok(operatorCatalogService.list());
        } catch (Exception e) {
            log.error("Error listing operator catalog: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error listing operator catalog: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        try {
            return ResponseEntity.ok(operatorCatalogService.get(id));
        } catch (Exception e) {
            log.error("Error getting operator catalog entry {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error getting operator catalog entry: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody OperatorCatalogRequestDTO request) {
        try {
            return ResponseEntity.ok(operatorCatalogService.create(request));
        } catch (Exception e) {
            log.error("Error creating operator catalog entry: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error creating operator catalog entry: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody OperatorCatalogRequestDTO request) {
        try {
            return ResponseEntity.ok(operatorCatalogService.update(id, request));
        } catch (Exception e) {
            log.error("Error updating operator catalog entry {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error updating operator catalog entry: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            operatorCatalogService.delete(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Operator catalog entry deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting operator catalog entry {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error deleting operator catalog entry: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/install")
    public ResponseEntity<?> install(@PathVariable String id, @RequestBody(required = false) OperatorCatalogActionRequestDTO request) {
        try {
            return ResponseEntity.ok(operatorCatalogService.install(id, request));
        } catch (Exception e) {
            log.error("Error installing operator {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error installing operator: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/upgrade")
    public ResponseEntity<?> upgrade(@PathVariable String id, @RequestBody(required = false) OperatorCatalogActionRequestDTO request) {
        try {
            return ResponseEntity.ok(operatorCatalogService.upgrade(id, request));
        } catch (Exception e) {
            log.error("Error upgrading operator {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error upgrading operator: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/uninstall")
    public ResponseEntity<?> uninstall(@PathVariable String id, @RequestBody(required = false) OperatorCatalogActionRequestDTO request) {
        try {
            return ResponseEntity.ok(operatorCatalogService.uninstall(id, request));
        } catch (Exception e) {
            log.error("Error uninstalling operator {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error uninstalling operator: " + e.getMessage());
        }
    }
}
