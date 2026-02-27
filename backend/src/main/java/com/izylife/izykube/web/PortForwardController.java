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

    @GetMapping("/check")
    public ResponseEntity<PortAvailabilityResponse> check(@RequestParam("port") int port) {
        return ResponseEntity.ok(portForwardService.checkLocalPort(port));
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
