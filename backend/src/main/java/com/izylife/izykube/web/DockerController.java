package com.izylife.izykube.web;

import com.izylife.izykube.services.DockerService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/docker")
public class DockerController {

    private final DockerService dockerService;

    public DockerController(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    @PostMapping("/pull")
    public String pullImage(@RequestParam String imageName,@RequestParam(required = false) String tag) {
        return dockerService.pullImage(imageName, tag);
    }

    @PostMapping("/create")
    public String createImage(@RequestParam MultipartFile dockerFile, @RequestParam String imageName, @RequestParam String tag) {
        return dockerService.createImage(dockerFile, imageName, tag);
    }

    @PostMapping("/start")
    public String startContainer(@RequestParam String containerId) {
        return dockerService.startContainer(containerId);
    }

    @PostMapping("/stop")
    public String stopContainer(@RequestParam String containerId) {
        return dockerService.stopContainer(containerId);
    }

    @DeleteMapping("/container")
    public String removeContainer(@RequestParam String containerId) {
        return dockerService.removeContainer(containerId);
    }

    @DeleteMapping("/image")
    public String removeImage(@RequestParam String imageId) {
        return dockerService.removeImage(imageId);
    }
}

