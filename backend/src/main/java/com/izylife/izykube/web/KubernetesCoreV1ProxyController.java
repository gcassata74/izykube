package com.izylife.izykube.web;

import com.izylife.izykube.dto.kube.PodEventDTO;
import com.izylife.izykube.dto.kube.PodLogDetailsDTO;
import com.izylife.izykube.services.KubernetesExplorerService;
import io.fabric8.kubernetes.api.model.Pod;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class KubernetesCoreV1ProxyController {

    private final KubernetesExplorerService explorerService;

    @GetMapping("/namespaces/{namespace}/pods/{podName}")
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

    @GetMapping(value = "/namespaces/{namespace}/pods/{podName}/log", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getPodLogs(@PathVariable String namespace,
                                             @PathVariable String podName,
                                             @RequestParam(required = false) String container,
                                             @RequestParam(name = "tailLines", defaultValue = "500") int tailLines) {
        PodLogDetailsDTO logs = explorerService.getPodLogsV1(namespace, podName, container, tailLines);
        if (logs == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Optional.ofNullable(logs.getLogs()).orElse(""));
    }

    @GetMapping("/namespaces/{namespace}/events")
    public ResponseEntity<List<PodEventDTO>> getEvents(@PathVariable String namespace,
                                                       @RequestParam(required = false) String fieldSelector) {
        String podName = extractFieldSelectorValue(fieldSelector, "involvedObject.name");
        String kind = extractFieldSelectorValue(fieldSelector, "involvedObject.kind");

        if (!StringUtils.hasText(podName) || (StringUtils.hasText(kind) && !"Pod".equals(kind))) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(List.of());
        }

        List<PodEventDTO> events = explorerService.getPodEvents(namespace, podName);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(events);
    }

    private String extractFieldSelectorValue(String selector, String key) {
        if (!StringUtils.hasText(selector) || !StringUtils.hasText(key)) {
            return null;
        }
        String prefix = key + "=";
        for (String part : selector.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(prefix)) {
                String value = trimmed.substring(prefix.length()).trim();
                return StringUtils.hasText(value) ? value : null;
            }
        }
        return null;
    }
}

