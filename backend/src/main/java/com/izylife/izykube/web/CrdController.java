package com.izylife.izykube.web;

import com.izylife.izykube.dto.crd.CrdDefinitionRequestDTO;
import com.izylife.izykube.services.CrdService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/crds")
public class CrdController {

    private final CrdService crdService;

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            return ResponseEntity.ok(crdService.list());
        } catch (Exception e) {
            log.error("Error listing CRDs: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error listing CRDs: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        try {
            return ResponseEntity.ok(crdService.get(id));
        } catch (Exception e) {
            log.error("Error getting CRD {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error getting CRD: " + e.getMessage());
        }
    }

    @GetMapping(value = "/{id}/yaml", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> getYaml(@PathVariable String id) {
        try {
            return ResponseEntity.ok(crdService.getYaml(id));
        } catch (Exception e) {
            log.error("Error generating CRD YAML {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error generating CRD YAML: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CrdDefinitionRequestDTO request) {
        try {
            return ResponseEntity.ok(crdService.create(request));
        } catch (Exception e) {
            log.error("Error creating CRD: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error creating CRD: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody CrdDefinitionRequestDTO request) {
        try {
            return ResponseEntity.ok(crdService.update(id, request));
        } catch (Exception e) {
            log.error("Error updating CRD {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error updating CRD: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            crdService.delete(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "CRD deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting CRD {}: {}", id, e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error deleting CRD: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
