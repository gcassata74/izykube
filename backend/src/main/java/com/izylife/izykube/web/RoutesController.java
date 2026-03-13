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

import com.izylife.izykube.dto.kube.RouteSummaryDTO;
import com.izylife.izykube.services.RouteService;
import com.izylife.izykube.web.request.RouteCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RoutesController {

    private static final Logger log = LoggerFactory.getLogger(RoutesController.class);
    private final RouteService routeService;

    @PostMapping
    public ResponseEntity<RouteSummaryDTO> create(@Valid @RequestBody RouteCreateRequest request) {
        return ResponseEntity.ok(routeService.create(request));
    }

    @DeleteMapping("/{namespace}/{name}")
    public ResponseEntity<Void> delete(@PathVariable String namespace, @PathVariable String name) {
        routeService.delete(namespace, name);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{namespace}/{name}")
    public ResponseEntity<RouteSummaryDTO> update(@PathVariable String namespace,
                                                    @PathVariable String name,
                                                    @Valid @RequestBody RouteCreateRequest request) {
        return ResponseEntity.ok(routeService.update(namespace, name, request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Invalid route request: {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleInvalidState(IllegalStateException ex) {
        log.warn("Route creation failed: {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
