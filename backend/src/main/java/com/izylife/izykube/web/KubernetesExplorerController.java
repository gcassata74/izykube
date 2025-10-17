package com.izylife.izykube.web;

import com.izylife.izykube.dto.kube.NamespaceDTO;
import com.izylife.izykube.dto.kube.NamespaceSummaryDTO;
import com.izylife.izykube.services.KubernetesExplorerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
