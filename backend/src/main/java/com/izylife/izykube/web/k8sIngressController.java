package com.izylife.izykube.web;

import com.izylife.izykube.model.ServiceInfo;
import com.izylife.izykube.services.K8sIngressService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/k8s/ingress")
public class k8sIngressController {

    private final K8sIngressService kubernetesService;

    public k8sIngressController(K8sIngressService kubernetesService) {
        this.kubernetesService = kubernetesService;
    }

    @PostMapping
    public String createIngress(@RequestBody List<ServiceInfo> services,@RequestParam(required = false) String namespace) {
        kubernetesService.createIngress(services,namespace);
        return "Ingress created successfully";
    }


}
