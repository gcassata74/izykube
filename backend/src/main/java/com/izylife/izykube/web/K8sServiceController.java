package com.izylife.izykube.web;

import com.izylife.izykube.dto.ServiceRequest;
import com.izylife.izykube.services.k8s.K8sServiceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/k8s/service")
public class K8sServiceController {

    private final K8sServiceService serviceService;

    public K8sServiceController(K8sServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @PostMapping
    public String createService(@RequestBody ServiceRequest request) {
        return serviceService.createService(request.getServiceName(), request.getDeploymentName(), request.getPort(), request.getTargetPort(), request.getNamespace());
    }

    @DeleteMapping
    public String deleteService(@RequestParam String serviceName,
                                 @RequestParam(required = false, defaultValue = "default") String namespace) {
        return serviceService.deleteService(serviceName, namespace);
    }
}
