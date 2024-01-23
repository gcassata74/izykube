package com.izylife.izykube.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class KubernetesResourceFactory {

    private final ObjectMapper objectMapper;

    public KubernetesResourceFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Class<? extends HasMetadata> getResourceClass(String kind) {
        switch (kind.toLowerCase()) {
            case "pod":
                return Pod.class;
            case "service":
                return Service.class;
            case "deployment":
                return Deployment.class;
            // Add more kinds as needed...
            default:
                throw new IllegalArgumentException("Unknown kind: " + kind);
        }
    }

    public GenericKubernetesResource createGenericResource(String json) throws IOException {
        return objectMapper.readValue(json, GenericKubernetesResource.class);
    }
}
