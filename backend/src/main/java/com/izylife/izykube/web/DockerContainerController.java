package com.izylife.izykube.web;

import com.izylife.izykube.model.ContainerInfo;
import com.izylife.izykube.model.RunContainerRequest;
import com.izylife.izykube.services.DockerContainerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/docker/container")
public class DockerContainerController {

    private final DockerContainerService dockerService;

    public DockerContainerController(DockerContainerService dockerService) {
        this.dockerService = dockerService;
    }

    @GetMapping("/all")
    public List<ContainerInfo> getContainers(@RequestParam(required = false, defaultValue = "false") boolean includeExited) {
        return dockerService.getContainers(includeExited);
    }

    @GetMapping("/{containerId}")
    public ContainerInfo getContainerById(@PathVariable String containerId,
                                          @RequestParam(required = false, defaultValue = "false") boolean includeExited) {
        return dockerService.getContainerById(containerId, includeExited);
    }

    @PostMapping("/run")
    public String runContainer(@RequestBody RunContainerRequest request) {
        String imageName = request.getImageName();
        String command = request.getCommand();
        String[] volumeBindings = request.getVolumeBindings();

        String[] commandArray = null;
        if (command != null) {
            commandArray = command.split(" ");
        }

        return dockerService.runContainer(imageName, commandArray, volumeBindings);
    }

    @PostMapping("/stop")
    public String stopContainer(@RequestParam String containerId) {
        return dockerService.stopContainer(containerId);
    }

    @DeleteMapping("/container")
    public String removeContainer(@RequestParam String containerId) {
        return dockerService.removeContainer(containerId);
    }


}

