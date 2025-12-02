package com.izylife.izykube.web;

import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.ResourceSyncStatusDTO;
import com.izylife.izykube.model.Namespace;
import com.izylife.izykube.services.NamespaceService;
import com.izylife.izykube.services.NamespaceResourceService;
import com.izylife.izykube.web.request.NamespaceRequest;
import jakarta.validation.Valid;
import javassist.tools.rmi.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/namespaces")
@RequiredArgsConstructor
public class NamespaceController {

    private final NamespaceService namespaceService;
    private final NamespaceResourceService namespaceResourceService;

    @GetMapping
    public List<Namespace> listNamespaces() {
        return namespaceService.listNamespaces();
    }

    @PostMapping
    public ResponseEntity<Namespace> createNamespace(@Valid @RequestBody NamespaceRequest request) {
        Namespace namespace = namespaceService.createNamespace(request.getName(), request.getDescription());
        return ResponseEntity.ok(namespace);
    }

    @PostMapping("/{identifier}/resources/{resourceId}/restart")
    public ResponseEntity<ResourceSyncStatusDTO> restartResource(
            @PathVariable("identifier") String namespaceIdentifier,
            @PathVariable String resourceId,
            @RequestBody NodeDTO nodePayload
    ) throws ObjectNotFoundException {
        ResourceSyncStatusDTO status = namespaceResourceService.restartResource(namespaceIdentifier, resourceId, nodePayload);
        return ResponseEntity.ok(status);
    }
}
