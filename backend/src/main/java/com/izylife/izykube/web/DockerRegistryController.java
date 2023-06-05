package com.izylife.izykube.web;

import com.izylife.izykube.services.DockerRegistryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/docker/registry")
public class DockerRegistryController {

    private final DockerRegistryService registryService;

    public DockerRegistryController(DockerRegistryService registryService) {
        this.registryService = registryService;
    }

    @PostMapping("/push")
    public String pushImageToLocalRegistry(@RequestParam String imageName, @RequestParam(required = false) String tag) {
        return registryService.pushImageToLocalRegistry(imageName,tag);
    }

    @DeleteMapping("/{imageName}")
    public ResponseEntity<String> deleteImage(@PathVariable String imageName) {
        try {
            String response = registryService.deleteImageFromLocalRegistry(imageName);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting image: " + e.getMessage());
        }
    }
}
