package com.izylife.izykube.services;

import org.springframework.stereotype.Service;

@Service
public class KeycloakService {
    private final DockerImageService dockerImageService;
    private final K8sPodService k8sPodService;

    public KeycloakService(DockerImageService dockerImageService, K8sPodService k8sPodService) {
        this.dockerImageService = dockerImageService;
        this.k8sPodService = k8sPodService;
    }

    public String deployKeycloak(String podName, String namespace) {
        String imageName = "jboss/keycloak";
        dockerImageService.pullImage(imageName, null);  // assumes latest tag if not provided
        return k8sPodService.createPod(podName, imageName, namespace);
    }
}
