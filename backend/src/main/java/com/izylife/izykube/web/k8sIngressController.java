package com.izylife.izykube.web;

import com.izylife.izykube.dto.IngressRequest;
import com.izylife.izykube.services.k8s.K8sIngressService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/k8s/ingress")
public class k8sIngressController {

    private final K8sIngressService kubernetesService;

    public k8sIngressController(K8sIngressService kubernetesService) {
        this.kubernetesService = kubernetesService;
    }

    @PostMapping
    public String createIngress(@RequestBody IngressRequest ingressRequest, @RequestParam(required = false) String namespace) {
        return kubernetesService.createIngress(ingressRequest, namespace);
    }


}
