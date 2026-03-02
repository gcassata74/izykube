package com.izylife.izykube.web;

import com.izylife.izykube.dto.kube.IngressSummaryDTO;
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
    public ResponseEntity<IngressSummaryDTO> create(@Valid @RequestBody RouteCreateRequest request) {
        return ResponseEntity.ok(routeService.create(request));
    }

    @DeleteMapping("/{namespace}/{name}")
    public ResponseEntity<Void> delete(@PathVariable String namespace, @PathVariable String name) {
        routeService.delete(namespace, name);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{namespace}/{name}")
    public ResponseEntity<IngressSummaryDTO> update(@PathVariable String namespace,
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
