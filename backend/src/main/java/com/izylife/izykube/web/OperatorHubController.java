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
