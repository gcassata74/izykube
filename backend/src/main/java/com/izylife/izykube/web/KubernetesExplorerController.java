package com.izylife.izykube.web;

import com.izylife.izykube.dto.kube.DeploymentLogsDTO;
import com.izylife.izykube.dto.kube.NamespaceDTO;
import com.izylife.izykube.dto.kube.NamespaceSummaryDTO;
import com.izylife.izykube.dto.kube.PodLogDTO;
import com.izylife.izykube.dto.kube.PodSummaryDTO;
import com.izylife.izykube.services.KubernetesExplorerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/kube")
public class KubernetesExplorerController {

    private final KubernetesExplorerService explorerService;

    @GetMapping("/namespaces")
    public ResponseEntity<List<NamespaceDTO>> listNamespaces() {
        List<NamespaceDTO> namespaces = explorerService.listNamespaces();
        return ResponseEntity.ok(namespaces);
    }

    @GetMapping("/summary")
    public ResponseEntity<NamespaceSummaryDTO> getSummary(@RequestParam(value = "namespace", defaultValue = "all") String namespace) {
        NamespaceSummaryDTO summary = explorerService.getNamespaceSummary(namespace);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/logs/pod")
    public ResponseEntity<PodLogDTO> getPodLogs(@RequestParam String namespace,
                                                @RequestParam String name,
                                                @RequestParam(defaultValue = "500") int tail) {
        PodLogDTO logs = explorerService.getPodLogs(namespace, name, tail);
        if (logs == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/logs/deployment")
    public ResponseEntity<DeploymentLogsDTO> getDeploymentLogs(@RequestParam String namespace,
                                                               @RequestParam String name,
                                                               @RequestParam(defaultValue = "500") int tail) {
        DeploymentLogsDTO logs = explorerService.getDeploymentLogs(namespace, name, tail);
        if (logs == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/deployments/{deployment}/pods")
    public ResponseEntity<List<PodSummaryDTO>> getDeploymentPods(@PathVariable("deployment") String deploymentName,
                                                                 @RequestParam String namespace) {
        List<PodSummaryDTO> pods = explorerService.getPodsByDeployment(namespace, deploymentName);
        return ResponseEntity.ok(pods);
    }
}
