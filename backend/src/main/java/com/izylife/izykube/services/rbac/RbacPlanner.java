package com.izylife.izykube.services.rbac;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC planner that generates ServiceAccounts + RoleBindings and patches workloads to use the selected ServiceAccount.
 *
 * <p>Important: this planner assumes that Role / ClusterRole resources themselves are defined elsewhere
 * (e.g. via a separate templating system). The RBAC block in the UI represents those.</p>
 */
public final class RbacPlanner {

    private RbacPlanner() {
    }

    public static RbacPlan buildRbacPlan(Graph graph, Options options) {
        Options effective = options == null ? new Options(false) : options;
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (graph == null) {
            return new RbacPlan(List.of(), List.of(), warnings, List.of("Graph is required"));
        }

        Map<String, WorkloadNode> workloadsById = Optional.ofNullable(graph.workloads()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .filter(node -> hasText(node.id()))
                .collect(Collectors.toMap(WorkloadNode::id, node -> node, (a, b) -> a, LinkedHashMap::new));

        Map<String, RbacBlockNode> rbacById = Optional.ofNullable(graph.rbacBlocks()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .filter(node -> hasText(node.id()))
                .collect(Collectors.toMap(RbacBlockNode::id, node -> node, (a, b) -> a, LinkedHashMap::new));

        List<Edge> rbacLinks = Optional.ofNullable(graph.edges()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .filter(edge -> "RBAC_LINK".equalsIgnoreCase(edge.type()))
                .toList();

        if (rbacLinks.isEmpty()) {
            return new RbacPlan(List.of(), List.of(), List.of("No RBAC_LINK edges found"), List.of());
        }

        // Basic validation + index links by workload
        Map<String, Set<String>> workloadToRbac = new LinkedHashMap<>();
        Map<GroupKey, Set<String>> groupToWorkloads = new LinkedHashMap<>();
        for (Edge edge : rbacLinks) {
            WorkloadNode workload = workloadsById.get(edge.fromWorkloadId());
            if (workload == null) {
                errors.add("RBAC_LINK references missing workload: " + edge.fromWorkloadId());
                continue;
            }
            RbacBlockNode rbac = rbacById.get(edge.toRbacBlockId());
            if (rbac == null) {
                errors.add("RBAC_LINK references missing RBAC block: " + edge.toRbacBlockId());
                continue;
            }
            String namespace = normalizeNamespace(workload.namespace());
            if (!hasText(namespace)) {
                errors.add("Workload '" + safeName(workload) + "' must have a namespace");
                continue;
            }
            if (!hasText(workload.name())) {
                errors.add("Workload '" + workload.id() + "' must have a name");
                continue;
            }
            if (!hasText(rbac.name())) {
                errors.add("RBAC block '" + rbac.id() + "' must have a name");
                continue;
            }
            if (rbac.kind() == RbacKind.ROLE && hasText(rbac.namespace())) {
                String rbacNs = normalizeNamespace(rbac.namespace());
                if (!Objects.equals(namespace, rbacNs)) {
                    errors.add("Role '" + rbac.name() + "' is namespaced; its namespace '" + rbacNs
                            + "' must match workload '" + safeName(workload) + "' namespace '" + namespace + "'");
                    continue;
                }
            }

            workloadToRbac.computeIfAbsent(workload.id(), ignored -> new LinkedHashSet<>()).add(rbac.id());
            groupToWorkloads.computeIfAbsent(new GroupKey(namespace, rbac.kind(), rbac.name()), ignored -> new LinkedHashSet<>())
                    .add(workload.id());
        }

        if (!errors.isEmpty()) {
            return new RbacPlan(List.of(), List.of(), warnings, errors);
        }

        // R3 strategy selection
        Map<GroupKey, Boolean> groupEligibleShared = new LinkedHashMap<>();
        for (Map.Entry<GroupKey, Set<String>> entry : groupToWorkloads.entrySet()) {
            GroupKey key = entry.getKey();
            Set<String> workloadIds = entry.getValue();
            boolean allSingles = workloadIds.stream()
                    .allMatch(id -> workloadToRbac.getOrDefault(id, Set.of()).size() == 1);
            boolean eligibleShared = workloadIds.size() >= 2 && allSingles;
            groupEligibleShared.put(key, eligibleShared);
        }

        // Pick exactly one SA per workload (R4)
        Map<String, String> workloadToServiceAccountName = new LinkedHashMap<>();
        Set<String> usedServiceAccountNames = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : workloadToRbac.entrySet()) {
            String workloadId = entry.getKey();
            WorkloadNode workload = workloadsById.get(workloadId);
            Set<String> rbacIds = entry.getValue();

            String namespace = normalizeNamespace(workload.namespace());
            int totalBlocks = rbacIds.size();

            ServiceAccountMode mode;
            if (totalBlocks > 1) {
                mode = ServiceAccountMode.DEDICATED;
            } else {
                // Exactly one RBAC block connected to the workload.
                String rbacId = rbacIds.iterator().next();
                RbacBlockNode rbac = rbacById.get(rbacId);
                GroupKey groupKey = new GroupKey(namespace, rbac.kind(), rbac.name());
                mode = groupEligibleShared.getOrDefault(groupKey, false) ? ServiceAccountMode.SHARED : ServiceAccountMode.DEDICATED;
            }

            String saBase = mode == ServiceAccountMode.DEDICATED
                    ? sanitizeDnsLabel(workload.name() + "-sa", 63)
                    : sanitizeDnsLabel(rbacById.get(rbacIds.iterator().next()).name() + "-sa", 63);

            // Collision handling for ServiceAccounts within the namespace.
            // Shared ServiceAccounts are intentionally reused across workloads in the same group.
            String saName = mode == ServiceAccountMode.SHARED
                    ? saBase
                    : resolveCollision(saBase, namespace, "sa", workloadId, usedServiceAccountNames);
            workloadToServiceAccountName.put(workloadId, saName);
            usedServiceAccountNames.add(saName);
        }

        // Create ServiceAccount resources (unique by namespace+name).
        Map<String, ServiceAccount> serviceAccounts = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : workloadToServiceAccountName.entrySet()) {
            WorkloadNode workload = workloadsById.get(entry.getKey());
            String namespace = normalizeNamespace(workload.namespace());
            String saName = entry.getValue();
            String key = namespace + "/" + saName;
            serviceAccounts.putIfAbsent(key, buildServiceAccount(namespace, saName));
        }

        // Create RoleBinding resources, one per (workload, rbacBlock), referencing the workload's chosen SA.
        Map<String, RoleBinding> roleBindings = new LinkedHashMap<>();
        Set<String> usedRoleBindingNames = new LinkedHashSet<>();
        for (Edge edge : rbacLinks) {
            WorkloadNode workload = workloadsById.get(edge.fromWorkloadId());
            RbacBlockNode rbac = rbacById.get(edge.toRbacBlockId());
            if (workload == null || rbac == null) {
                continue;
            }
            String namespace = normalizeNamespace(workload.namespace());
            String saName = workloadToServiceAccountName.get(workload.id());

            String rbBase = sanitizeDnsLabel(saName + "-" + rbac.name() + "-rb", 63);
            String rbName = resolveCollision(
                    rbBase,
                    namespace,
                    "rb",
                    namespace + "|" + saName + "|" + rbac.kind() + "|" + rbac.name(),
                    usedRoleBindingNames
            );
            usedRoleBindingNames.add(rbName);

            RoleBinding binding = buildRoleBinding(namespace, rbName, saName, rbac);
            roleBindings.putIfAbsent(namespace + "/" + rbName, binding);
        }

        // Create workload patches.
        List<WorkloadPatch> patches = new ArrayList<>();
        for (Map.Entry<String, String> entry : workloadToServiceAccountName.entrySet()) {
            WorkloadNode workload = workloadsById.get(entry.getKey());
            String namespace = normalizeNamespace(workload.namespace());
            String desired = entry.getValue();

            String existing = readExistingServiceAccountName(workload);
            if (hasText(existing) && !Objects.equals(existing, desired)) {
                if (effective.strictServiceAccountName()) {
                    errors.add("Workload '" + safeName(workload) + "' already defines serviceAccountName '" + existing + "'");
                    continue;
                }
                warnings.add("Workload '" + safeName(workload) + "' serviceAccountName overridden (" + existing + " -> " + desired + ")");
            }

            Map<String, Object> patch = buildMergePatch(workload.kind(), desired);
            if (patch == null) {
                errors.add("Unsupported workload kind for serviceAccountName patch: " + workload.kind());
                continue;
            }
            patches.add(new WorkloadPatch(workload.id(), namespace, workload.kind(), patch));
        }

        if (!errors.isEmpty()) {
            return new RbacPlan(List.of(), patches, warnings, errors);
        }

        // Stable output ordering: ServiceAccounts then RoleBindings, both sorted by namespace/name.
        List<String> yamls = new ArrayList<>();
        serviceAccounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Serialization.asYaml(entry.getValue()))
                .forEach(yamls::add);
        roleBindings.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Serialization.asYaml(entry.getValue()))
                .forEach(yamls::add);

        return new RbacPlan(yamls, patches, warnings, errors);
    }

    private static ServiceAccount buildServiceAccount(String namespace, String name) {
        validateDns1123Label(name, "ServiceAccount");
        return new ServiceAccountBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .endMetadata()
                .build();
    }

    private static RoleBinding buildRoleBinding(String namespace, String bindingName, String serviceAccountName, RbacBlockNode rbac) {
        validateDns1123Label(bindingName, "RoleBinding");
        String roleRefKind = rbac.kind() == RbacKind.CLUSTER_ROLE ? "ClusterRole" : "Role";

        return new RoleBindingBuilder()
                .withNewMetadata()
                .withName(bindingName)
                .withNamespace(namespace)
                .endMetadata()
                .addNewSubject()
                .withKind("ServiceAccount")
                .withName(serviceAccountName)
                .withNamespace(namespace)
                .endSubject()
                .withNewRoleRef()
                .withApiGroup("rbac.authorization.k8s.io")
                .withKind(roleRefKind)
                .withName(rbac.name())
                .endRoleRef()
                .build();
    }

    /**
     * Builds a JSON-merge-patch-like nested map that sets serviceAccountName in the correct place.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildMergePatch(WorkloadKind kind, String serviceAccountName) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case DEPLOYMENT, STATEFUL_SET, JOB -> Map.of(
                    "spec", Map.of(
                            "template", Map.of(
                                    "spec", Map.of(
                                            "serviceAccountName", serviceAccountName
                                    )
                            )
                    )
            );
            case CRON_JOB -> Map.of(
                    "spec", Map.of(
                            "jobTemplate", Map.of(
                                    "spec", Map.of(
                                            "template", Map.of(
                                                    "spec", Map.of(
                                                            "serviceAccountName", serviceAccountName
                                                    )
                                            )
                                    )
                            )
                    )
            );
        };
    }

    private static String readExistingServiceAccountName(WorkloadNode workload) {
        if (workload == null || workload.spec() == null) {
            return null;
        }
        Map<String, Object> spec = workload.spec();
        try {
            return switch (workload.kind()) {
                case DEPLOYMENT, STATEFUL_SET, JOB -> getString(spec, "template", "spec", "serviceAccountName");
                case CRON_JOB -> getString(spec, "jobTemplate", "spec", "template", "spec", "serviceAccountName");
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static String getString(Map<String, Object> root, String... path) {
        Object current = root;
        for (int i = 0; i < path.length; i++) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(path[i]);
        }
        return current == null ? null : String.valueOf(current);
    }

    private static String resolveCollision(String base, String namespace, String discriminator, String entropy, java.util.Collection<String> usedNames) {
        String candidate = base;
        if (!usedNames.contains(candidate)) {
            return candidate;
        }
        String suffix = sha1hex(namespace + "|" + discriminator + "|" + entropy).substring(0, 4);
        String withSuffix = ensureDnsLabelSuffix(candidate, suffix, 63);
        if (!usedNames.contains(withSuffix)) {
            return withSuffix;
        }
        // Last resort: include a deterministic second suffix.
        String suffix2 = sha1hex(namespace + "|" + discriminator + "|" + entropy + "|2").substring(0, 4);
        return ensureDnsLabelSuffix(candidate, suffix2, 63);
    }

    private static String safeName(WorkloadNode node) {
        return hasText(node.name()) ? node.name() : node.id();
    }

    private static String normalizeNamespace(String ns) {
        String value = ns == null ? "" : ns.trim();
        return value.isEmpty() ? "default" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String sanitizeDnsLabel(String raw, int maxLen) {
        String value = (raw == null ? "" : raw.trim()).toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9-]+", "-");
        value = value.replaceAll("^-+", "");
        value = value.replaceAll("-+$", "");
        value = value.replaceAll("-{2,}", "-");
        if (value.isEmpty()) {
            value = "rbac";
        }
        if (value.length() > maxLen) {
            value = value.substring(0, maxLen);
            value = value.replaceAll("-+$", "");
        }
        if (value.isEmpty()) {
            value = "rbac";
        }
        return value;
    }

    private static String ensureDnsLabelSuffix(String base, String suffix, int maxLen) {
        String normalized = sanitizeDnsLabel(base, maxLen);
        String safeSuffix = sanitizeDnsLabel(suffix, 8);
        int available = Math.max(1, maxLen - safeSuffix.length() - 1);
        String prefix = sanitizeDnsLabel(normalized, available);
        return prefix + "-" + safeSuffix;
    }

    private static void validateDns1123Label(String name, String resourceType) {
        if (!hasText(name)) {
            throw new IllegalArgumentException(resourceType + " name is required");
        }
        if (name.length() > 63) {
            throw new IllegalArgumentException(resourceType + " name must be <= 63 characters");
        }
        if (!name.matches("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")) {
            throw new IllegalArgumentException(resourceType + " name must be a valid DNS-1123 label (lowercase alphanumeric, '-', start/end alphanumeric)");
        }
    }

    private static String sha1hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(String.valueOf(input).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "0000000000000000000000000000000000000000";
        }
    }

    private enum ServiceAccountMode {
        DEDICATED,
        SHARED
    }

    private record GroupKey(String namespace, RbacKind kind, String name) {
    }

    public record Options(boolean strictServiceAccountName) {
    }

    public record RbacPlan(List<String> resources,
                           List<WorkloadPatch> workloadPatches,
                           List<String> warnings,
                           List<String> errors) {
    }

    public record WorkloadPatch(String workloadId,
                                String namespace,
                                WorkloadKind kind,
                                Map<String, Object> mergePatch) {
    }

    public record Graph(List<WorkloadNode> workloads,
                        List<RbacBlockNode> rbacBlocks,
                        List<Edge> edges) {
    }

    public record WorkloadNode(String id,
                               WorkloadKind kind,
                               String name,
                               String namespace,
                               Map<String, Object> spec) {
    }

    public record RbacBlockNode(String id,
                                RbacKind kind,
                                String name,
                                String namespace) {
    }

    public record Edge(String id,
                       String type,
                       String fromWorkloadId,
                       String toRbacBlockId) {
    }

    public enum WorkloadKind {
        DEPLOYMENT,
        STATEFUL_SET,
        JOB,
        CRON_JOB
    }

    public enum RbacKind {
        ROLE,
        CLUSTER_ROLE
    }
}
