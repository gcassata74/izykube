package com.izylife.izykube.web;

import com.izylife.izykube.services.k8s.K8sPodService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/keycloak")
public class KeycloakController {

    private final K8sPodService podService;

    public KeycloakController(K8sPodService podService) {
        this.podService = podService;
    }

    @PostMapping
    public String createKeycloakPod(@RequestParam String podName, @RequestParam(required = false) String namespace) {
        String imageName = "jboss/keycloak";  // this is the official Keycloak image, modify if needed
        return podService.createPod(podName, imageName, namespace);
    }

    @DeleteMapping
    public String deleteKeycloakPod(@RequestParam String podName, @RequestParam(required = false) String namespace) {
        return podService.deletePod(podName, namespace);
    }

    // Endpoint for configuring Keycloak would go here, but it's not clear what you need so I can't provide a concrete example.
    // It would likely require a POST request with a complex body to provide configuration options.
    // It would also likely need to interact with the Keycloak REST API, which would need a new service to handle.
}
