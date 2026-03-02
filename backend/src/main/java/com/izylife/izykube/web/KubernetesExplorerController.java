package com.izylife.izykube.web;

import com.izylife.izykube.dto.kube.DeploymentLogsDTO;
import com.izylife.izykube.dto.kube.IngressClassSummaryDTO;
import com.izylife.izykube.dto.kube.IngressGatewayInfoDTO;
import com.izylife.izykube.dto.kube.NamespaceDTO;
import com.izylife.izykube.dto.kube.NamespaceSummaryDTO;
import com.izylife.izykube.dto.kube.PodEventDTO;
import com.izylife.izykube.dto.kube.PodLogDTO;
import com.izylife.izykube.dto.kube.PodLogDetailsDTO;
import com.izylife.izykube.dto.kube.PodSummaryDTO;
import com.izylife.izykube.services.KubernetesExplorerService;
import io.fabric8.kubernetes.api.model.Pod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
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
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(namespaces);
    }

    @GetMapping("/summary")
    public ResponseEntity<NamespaceSummaryDTO> getSummary(@RequestParam(value = "namespace", defaultValue = "all") String namespace) {
        NamespaceSummaryDTO summary = explorerService.getNamespaceSummary(namespace);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(summary);
    }

    @GetMapping("/ingress-classes")
    public ResponseEntity<List<IngressClassSummaryDTO>> listIngressClasses() {
        List<IngressClassSummaryDTO> ingressClasses = explorerService.listIngressClasses();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ingressClasses);
    }

    @GetMapping("/ingress-gateway")
    public ResponseEntity<IngressGatewayInfoDTO> getIngressGateway() {
        IngressGatewayInfoDTO gateway = explorerService.getIngressGatewayInfo();
        if (gateway == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(gateway);
    }

    @GetMapping("/logs/pod")
    public ResponseEntity<PodLogDTO> getPodLogs(@RequestParam String namespace,
                                                @RequestParam String name,
                                                @RequestParam(defaultValue = "500") int tail) {
        PodLogDTO logs = explorerService.getPodLogs(namespace, name, tail);
        if (logs == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(logs);
    }

    @GetMapping("/pods/{namespace}/{podName}")
    public ResponseEntity<Pod> getPod(@PathVariable String namespace,
                                      @PathVariable String podName) {
        Pod pod = explorerService.getPod(namespace, podName);
        if (pod == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(pod);
    }

    @GetMapping("/pods/{namespace}/{podName}/logs")
    public ResponseEntity<PodLogDetailsDTO> getPodLogsV1(@PathVariable String namespace,
                                                         @PathVariable String podName,
                                                         @RequestParam(required = false) String container,
                                                         @RequestParam(defaultValue = "500") int tail) {
        PodLogDetailsDTO logs = explorerService.getPodLogsV1(namespace, podName, container, tail);
        if (logs == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(logs);
    }

    @GetMapping("/pods/{namespace}/{podName}/events")
    public ResponseEntity<List<PodEventDTO>> getPodEvents(@PathVariable String namespace,
                                                         @PathVariable String podName) {
        List<PodEventDTO> events = explorerService.getPodEvents(namespace, podName);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(events);
    }

    @GetMapping("/logs/deployment")
    public ResponseEntity<DeploymentLogsDTO> getDeploymentLogs(@RequestParam String namespace,
                                                               @RequestParam String name,
                                                               @RequestParam(defaultValue = "500") int tail) {
        DeploymentLogsDTO logs = explorerService.getDeploymentLogs(namespace, name, tail);
        if (logs == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(logs);
    }

    @GetMapping("/deployments/{deployment}/pods")
    public ResponseEntity<List<PodSummaryDTO>> getDeploymentPods(@PathVariable("deployment") String deploymentName,
                                                                 @RequestParam String namespace) {
        List<PodSummaryDTO> pods = explorerService.getPodsByDeployment(namespace, deploymentName);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(pods);
    }
}
