package com.izylife.izykube.services;

import com.izylife.izykube.model.Namespace;
import com.izylife.izykube.repositories.NamespaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NamespaceService {

    private final NamespaceRepository namespaceRepository;

    public List<Namespace> listNamespaces() {
        return namespaceRepository.findAllSorted();
    }

    public Namespace createNamespace(String name, String description) {
        String normalized = normalizeName(name);
        Namespace namespace = namespaceRepository.findByNameIgnoreCase(normalized)
                .orElseGet(() -> {
                    Namespace ns = new Namespace();
                    ns.setName(normalized);
                    return ns;
                });
        if (description != null) {
            namespace.setDescription(description);
        }
        return namespaceRepository.save(namespace);
    }

    public Namespace ensureNamespaceExists(String name) {
        String normalized = normalizeName(name);
        return namespaceRepository.findByNameIgnoreCase(normalized)
                .orElseGet(() -> {
                    Namespace namespace = new Namespace();
                    namespace.setName(normalized);
                    namespace.setDescription("Auto-created namespace");
                    return namespaceRepository.save(namespace);
                });
    }

    public void deleteNamespaceRecord(String name) {
        String normalized = normalizeName(name);
        if ("default".equalsIgnoreCase(normalized)) {
            return;
        }
        namespaceRepository.findByNameIgnoreCase(normalized)
                .ifPresent(namespaceRepository::delete);
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            return "default";
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
