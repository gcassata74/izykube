package com.izylife.izykube.web;

import com.izylife.izykube.model.DeploymentRequest;
import com.izylife.izykube.services.K8sDeploymentService;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/k8s/deployments")
public class K8sDeploymentController {

    private final K8sDeploymentService deploymentService;

    public K8sDeploymentController(K8sDeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @GetMapping
    public List<Deployment> getDeployments(@RequestParam(required = false) String namespace) {
        return deploymentService.getDeployments(namespace);
    }

    @PostMapping
    public String createDeployment(@RequestBody DeploymentRequest request) {
        return deploymentService.createDeployment(request);
    }

    @DeleteMapping
    public Boolean deleteDeployment(@RequestParam String deploymentName, @RequestParam(required = false) String namespace) {
        return deploymentService.deleteDeployment(deploymentName, namespace);
    }

    @PatchMapping
    public String updateDeploymentReplicas(@RequestParam String deploymentName, @RequestParam(required = false) String namespace, @RequestParam int replicas) {
        return deploymentService.updateReplicas(deploymentName, replicas, namespace );
    }



}
