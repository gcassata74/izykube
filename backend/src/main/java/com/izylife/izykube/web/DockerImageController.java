package com.izylife.izykube.web;

import com.izylife.izykube.dto.docker.LocalImageDTO;
import com.izylife.izykube.services.docker.DockerImageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/docker/image")
public class DockerImageController {

    private final DockerImageService dockerService;

    public DockerImageController(DockerImageService dockerService) {
        this.dockerService = dockerService;
    }

    @GetMapping
    public String pullImage(@RequestParam String imageName,@RequestParam(required = false) String tag) {
        return dockerService.pullImage(imageName, tag);
    }

    @GetMapping("/local")
    public List<LocalImageDTO> listLocalImages() {
        return dockerService.listLocalImages();
    }

    @PostMapping
    public String createImage(@RequestParam MultipartFile dockerFile, @RequestParam String imageName, @RequestParam String tag) {
        return dockerService.createImage(dockerFile, imageName, tag);
    }

    @DeleteMapping
    public String removeImage(@RequestParam String imageId) {
        return dockerService.removeImage(imageId);
    }
}
