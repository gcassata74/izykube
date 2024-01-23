package com.izylife.izykube.web;

import com.izylife.izykube.services.KubernetesClientService;
import io.fabric8.kubernetes.api.model.HasMetadata;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/kubernetes")
public class KubernetesController {

    private final KubernetesClientService kubernetesClientService;

    public KubernetesController(KubernetesClientService kubernetesClientService) {
        this.kubernetesClientService = kubernetesClientService;
    }

    @PostMapping
    public HasMetadata createOrUpdate(@RequestBody String json, @RequestParam(required = false) String namespace) throws Exception {
        return kubernetesClientService.createOrUpdate(json, namespace);
    }

    @DeleteMapping
    public void delete(@RequestBody String json, @RequestParam(required = false) String namespace) throws Exception {
        kubernetesClientService.delete(json, namespace);
    }

    @GetMapping
    public Object getAll(@RequestParam String kind, @RequestParam(required = false) String namespace) {
        return kubernetesClientService.getAll(kind, namespace);
    }
}