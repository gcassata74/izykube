package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.ServiceAccountDTO;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class RbacProcessor {

    public List<String> createTemplates(String workloadNamespace, List<NodeDTO> nodes) {
        String namespace = StringUtils.hasText(workloadNamespace) ? workloadNamespace : "default";

        Map<String, String> seenServiceAccountNames = new LinkedHashMap<>();
        for (NodeDTO node : Optional.ofNullable(nodes).orElse(List.of())) {
            if (!(node instanceof ServiceAccountDTO sa)) {
                continue;
            }
            String saName = normalizeName(sa.getName());
            if (!StringUtils.hasText(saName)) {
                throw new IllegalArgumentException("ServiceAccount name is required for RBAC generation");
            }
            validateDns1123Subdomain(saName);
            String existingId = seenServiceAccountNames.putIfAbsent(saName, sa.getId());
            if (existingId != null && !existingId.equals(sa.getId())) {
                throw new IllegalArgumentException("Duplicate ServiceAccount name '" + saName + "' in namespace '" + namespace + "'");
            }
        }

        List<String> templates = new ArrayList<>();
        Map<String, String> usedRoleBindingNames = new LinkedHashMap<>();
        for (NodeDTO node : Optional.ofNullable(nodes).orElse(List.of())) {
            if (!(node instanceof ServiceAccountDTO sa)) {
                continue;
            }
            String profile = Optional.ofNullable(sa.getRbacProfile()).orElse("NONE").trim().toUpperCase(Locale.ROOT);
            if ("NONE".equals(profile) || profile.isBlank()) {
                continue;
            }
            String clusterRoleName = switch (profile) {
                case "VIEW" -> "view";
                case "EDIT" -> "edit";
                case "ADMIN" -> "admin";
                default -> throw new IllegalArgumentException("Unsupported RBAC profile for ServiceAccount " + sa.getName() + ": " + profile);
            };

            String saName = normalizeName(sa.getName());
            validateDns1123Subdomain(saName);

            String baseBindingName = saName + "-" + clusterRoleName;
            String bindingName = baseBindingName;
            if (usedRoleBindingNames.containsKey(bindingName) && !sa.getId().equals(usedRoleBindingNames.get(bindingName))) {
                bindingName = baseBindingName + "-" + shortId(sa.getId());
            }
            usedRoleBindingNames.putIfAbsent(bindingName, sa.getId());

            RoleBinding binding = new RoleBindingBuilder()
                    .withNewMetadata()
                    .withName(bindingName)
                    .withNamespace(namespace)
                    .endMetadata()
                    .addNewSubject()
                    .withKind("ServiceAccount")
                    .withName(saName)
                    .withNamespace(namespace)
                    .endSubject()
                    .withNewRoleRef()
                    .withApiGroup("rbac.authorization.k8s.io")
                    .withKind("ClusterRole")
                    .withName(clusterRoleName)
                    .endRoleRef()
                    .build();
            templates.add(Serialization.asYaml(binding));
        }

        return templates;
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }

    private void validateDns1123Subdomain(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ServiceAccount name is required");
        }
        if (name.length() > 253) {
            throw new IllegalArgumentException("ServiceAccount name must be <= 253 characters");
        }
        if (!name.matches("^[a-z0-9]([a-z0-9-.]*[a-z0-9])?$")) {
            throw new IllegalArgumentException("ServiceAccount name must be a valid DNS-1123 subdomain (lowercase alphanumeric, '-', '.', start/end alphanumeric)");
        }
    }

    private String shortId(String id) {
        if (id == null) {
            return "sa";
        }
        String normalized = id.replaceAll("[^a-zA-Z0-9]+", "");
        if (normalized.length() <= 6) {
            return normalized.isBlank() ? "sa" : normalized.toLowerCase(Locale.ROOT);
        }
        return normalized.substring(0, 6).toLowerCase(Locale.ROOT);
    }
}
