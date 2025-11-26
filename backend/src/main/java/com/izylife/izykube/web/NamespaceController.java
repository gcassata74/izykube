package com.izylife.izykube.web;

import com.izylife.izykube.model.Namespace;
import com.izylife.izykube.services.NamespaceService;
import com.izylife.izykube.web.request.NamespaceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/namespaces")
@RequiredArgsConstructor
public class NamespaceController {

    private final NamespaceService namespaceService;

    @GetMapping
    public List<Namespace> listNamespaces() {
        return namespaceService.listNamespaces();
    }

    @PostMapping
    public ResponseEntity<Namespace> createNamespace(@Valid @RequestBody NamespaceRequest request) {
        Namespace namespace = namespaceService.createNamespace(request.getName(), request.getDescription());
        return ResponseEntity.ok(namespace);
    }
}
