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

import com.izylife.izykube.services.PortForwardService;
import com.izylife.izykube.web.request.PortForwardRequest;
import com.izylife.izykube.web.response.PortForwardResponse;
import com.izylife.izykube.web.response.PortAvailabilityResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/port-forward")
@RequiredArgsConstructor
public class PortForwardController {

    private static final Logger log = LoggerFactory.getLogger(PortForwardController.class);
    private final PortForwardService portForwardService;

    @PostMapping("/start")
    public ResponseEntity<PortForwardResponse> start(@Valid @RequestBody PortForwardRequest request) {
        return ResponseEntity.ok(portForwardService.start(request));
    }

    @PostMapping("/stop")
    public ResponseEntity<PortForwardResponse> stop(@Valid @RequestBody PortForwardRequest request) {
        return ResponseEntity.ok(portForwardService.stop(request));
    }

    @GetMapping("/active")
    public ResponseEntity<java.util.List<PortForwardResponse>> listActive() {
        return ResponseEntity.ok(portForwardService.listActive());
    }

    @GetMapping("/entries")
    public ResponseEntity<java.util.List<PortForwardResponse>> listEntries() {
        return ResponseEntity.ok(portForwardService.listForwards());
    }

    @PostMapping("/delete")
    public ResponseEntity<Void> delete(@Valid @RequestBody PortForwardRequest request) {
        portForwardService.deleteForward(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check")
    public ResponseEntity<PortAvailabilityResponse> check(@RequestParam("port") int port) {
        return ResponseEntity.ok(portForwardService.checkLocalPort(port));
    }

    @GetMapping("/status")
    public ResponseEntity<PortForwardResponse> status(@RequestParam("namespace") String namespace,
                                                      @RequestParam("serviceName") String serviceName,
                                                      @RequestParam("targetPort") int targetPort) {
        return ResponseEntity.ok(portForwardService.getStatus(namespace, serviceName, targetPort));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Invalid port forward request: {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleInvalidState(IllegalStateException ex) {
        log.warn("Port forward not allowed: {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
