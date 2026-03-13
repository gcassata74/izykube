/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.izylife.izykube.web;

import com.izylife.izykube.dto.kube.DeploymentLogsDTO;
import com.izylife.izykube.dto.kube.IstioGatewayInfoDTO;
import com.izylife.izykube.dto.kube.NamespaceDTO;
import com.izylife.izykube.dto.kube.NamespaceSummaryDTO;
import com.izylife.izykube.dto.kube.WorkloadHealthDTO;
import com.izylife.izykube.dto.kube.PodEventDTO;
import com.izylife.izykube.dto.kube.PodLogDTO;
import com.izylife.izykube.dto.kube.PodLogDetailsDTO;
import com.izylife.izykube.dto.kube.PodSummaryDTO;
import com.izylife.izykube.services.KubernetesExplorerService;
import io.fabric8.kubernetes.api.model.Pod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @GetMapping("/istio-gateway")
    public ResponseEntity<IstioGatewayInfoDTO> getIstioGateway() {
        IstioGatewayInfoDTO gateway = explorerService.getIstioGatewayInfo();
        if (gateway == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(gateway);
    }

    @GetMapping("/ca-cert")
    public ResponseEntity<byte[]> getInternalCaCertificate() {
        byte[] cert = explorerService.getInternalCaCertificate();
        if (cert == null || cert.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"izykube-ca.crt\"")
                .contentType(MediaType.parseMediaType("application/x-pem-file"))
                .body(cert);
    }

    @GetMapping("/workloads/health")
    public ResponseEntity<List<WorkloadHealthDTO>> getWorkloadHealth(@RequestParam String namespace) {
        List<WorkloadHealthDTO> health = explorerService.getWorkloadHealth(namespace);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(health);
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

    @GetMapping("/logs/workload")
    public ResponseEntity<DeploymentLogsDTO> getWorkloadLogs(@RequestParam String kind,
                                                             @RequestParam String namespace,
                                                             @RequestParam String name,
                                                             @RequestParam(defaultValue = "500") int tail,
                                                             @RequestParam(defaultValue = "false") boolean previous) {
        DeploymentLogsDTO logs = explorerService.getWorkloadLogs(kind, namespace, name, tail, previous);
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

    @PostMapping("/deployments/{deployment}/mesh")
    public ResponseEntity<Void> updateDeploymentMesh(@PathVariable("deployment") String deploymentName,
                                                     @RequestParam String namespace,
                                                     @RequestParam(defaultValue = "false") boolean enabled) {
        explorerService.setDeploymentMesh(namespace, deploymentName, enabled);
        return ResponseEntity.noContent().build();
    }
}
