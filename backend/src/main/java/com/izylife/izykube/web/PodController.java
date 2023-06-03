package com.izylife.izykube.web;
import com.izylife.izykube.services.PodService;
import io.fabric8.kubernetes.api.model.Pod;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pods")
public class PodController {

    private final PodService podService;

    public PodController(PodService podService) {
        this.podService = podService;
    }

    @PostMapping
    public String createPod(@RequestParam String podName, @RequestParam String imageName, @RequestParam(required = false) String namespace) {
        return podService.createPod(podName, imageName, namespace);
    }

    @GetMapping("/all")
    public List<Pod> getAllPods() {
        return podService.getAllPods();
    }

    @GetMapping
    public Pod getPodByName(@RequestParam String podName, @RequestParam(required = false) String namespace) {
        return podService.getPodByName(podName, namespace);
    }

    @DeleteMapping
    public String deletePod(@RequestParam String podName, @RequestParam(required = false) String namespace) {
        return podService.deletePod(podName, namespace);
    }
}
