package com.izylife.izykube.web;

import com.izylife.izykube.services.DockerRegistryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
