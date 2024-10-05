package com.izylife.izykube.web;

import com.izylife.izykube.dto.GenericResponseDTO;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.services.TemplateService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequestMapping("/api/template")
@AllArgsConstructor
public class TemplateController {

    private TemplateService templateService;

    @PostMapping("/{id}")
    @ResponseBody
    public ResponseEntity<GenericResponseDTO> createTemplate(@PathVariable String id) {
        GenericResponseDTO response = new GenericResponseDTO();
        try {
            templateService.createTemplate(id);
            response.setMessage("The template was created successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setError("The template could not be created");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<GenericResponseDTO> updateTemplate(@PathVariable String id, @RequestBody ClusterDTO clusterDTO) {
        GenericResponseDTO response = new GenericResponseDTO();
        try {
            templateService.updateTemplate(id, clusterDTO);
            response.setMessage("The template was created successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setError("The template could not be created");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<GenericResponseDTO> deleteTemplate(@PathVariable String id) {
        GenericResponseDTO response = new GenericResponseDTO();
        try {
            templateService.deleteTemplate(id);
            response.setMessage("The template was created successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setError("The template could not be created");
            return ResponseEntity.badRequest().body(response);
        }
    }

}
