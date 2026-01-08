package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.AccessPolicyBindingStrategy;
import com.izylife.izykube.dto.cluster.AccessPolicyDTO;
import com.izylife.izykube.dto.cluster.AccessPolicyRuleDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.JobDTO;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.PodDTO;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

@Service
public class RbacProcessor {

    public record Generation(List<String> yamls) {}

    public Generation generateAndApply(String diagramNamespace, List<NodeDTO> nodes, List<LinkDTO> links) {
        String namespace = StringUtils.hasText(diagramNamespace) ? diagramNamespace.trim() : "default";

        Map<String, NodeDTO> nodesById = Optional.ofNullable(nodes).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .filter(node -> StringUtils.hasText(node.getId()))
                .collect(Collectors.toMap(NodeDTO::getId, node -> node, (a, b) -> a, LinkedHashMap::new));

        List<AccessPolicyDTO> policies = Optional.ofNullable(nodes).orElse(List.of()).stream()
                .filter(node -> node instanceof AccessPolicyDTO)
                .map(node -> (AccessPolicyDTO) node)
                .sorted(Comparator
                        .comparing((AccessPolicyDTO policy) -> resolveNamespace(policy, namespace))
                        .thenComparing(policy -> sanitizeName(policy.getName(), 63))
                        .thenComparing(policy -> Optional.ofNullable(policy.getId()).orElse("")))
                .toList();

        if (policies.isEmpty()) {
            return new Generation(List.of());
        }

        Map<String, String> seenPolicyNames = new LinkedHashMap<>();
        for (AccessPolicyDTO policy : policies) {
            String policyNs = resolveNamespace(policy, namespace);
            if (!Objects.equals(policyNs, namespace)) {
                throw new IllegalArgumentException("AccessPolicy '" + safeName(policy) + "' must be in the cluster namespace '" + namespace + "'");
            }
            String rawName = normalize(policy.getName());
            if (!StringUtils.hasText(rawName)) {
                throw new IllegalArgumentException("AccessPolicy name is required");
            }
            String existingId = seenPolicyNames.putIfAbsent(policyNs + "/" + rawName, policy.getId());
            if (existingId != null && !existingId.equals(policy.getId())) {
                throw new IllegalArgumentException("Duplicate AccessPolicy name '" + rawName + "' in namespace '" + policyNs + "'");
            }
            validateRules(policy);
        }

        Map<String, ServiceAccount> serviceAccounts = new LinkedHashMap<>();
        Map<String, Role> roles = new LinkedHashMap<>();
        Map<String, RoleBinding> roleBindings = new LinkedHashMap<>();

        for (AccessPolicyDTO policy : policies) {
            String policyNs = resolveNamespace(policy, namespace);
            String roleName = roleName(policy);
            roles.putIfAbsent(key("Role", policyNs, roleName), buildRole(policy, policyNs, roleName));

            List<LinkDTO> connectedLinks = Optional.ofNullable(links).orElse(List.of()).stream()
                    .filter(Objects::nonNull)
                    .filter(link -> StringUtils.hasText(link.getSource()) && StringUtils.hasText(link.getTarget()))
                    .filter(link -> Objects.equals(policy.getId(), link.getSource()) || Objects.equals(policy.getId(), link.getTarget()))
                    .toList();
        List<Target> targets = connectedLinks.stream()
                    .map(link -> resolveTarget(policy, link, nodesById))
                    .filter(Objects::nonNull)
                    .sorted(Comparator
                            .comparing((Target target) -> target.type.sortKey)
                            .thenComparing(target -> sanitizeName(target.displayName, 63))
                            .thenComparing(target -> Optional.ofNullable(target.id).orElse("")))
                    .toList();

            if (targets.isEmpty()) {
                throw new IllegalArgumentException("AccessPolicy '" + safeName(policy) + "' is not applied to any target");
            }

            Set<String> usedServiceAccountNames = new LinkedHashSet<>();
            for (Target target : targets) {
                if (target.type == TargetType.WORKLOAD) {
                    String workloadName = normalize(target.displayName);
                    if (!StringUtils.hasText(workloadName)) {
                        throw new IllegalArgumentException("AccessPolicy '" + safeName(policy) + "' applies to a workload with no name");
                    }
                    String saName = resolveServiceAccountName(policy, workloadName, target.id, usedServiceAccountNames);
                    usedServiceAccountNames.add(saName);
                    String saKey = key("ServiceAccount", policyNs, saName);
                    serviceAccounts.putIfAbsent(saKey, buildServiceAccount(policyNs, saName));

                    applyServiceAccountName(target.node, saName);

                    String bindingName = bindingNameForWorkload(policy, workloadName, target.id);
                    RoleBinding binding = buildRoleBindingForServiceAccount(policyNs, bindingName, roleName, saName);
                    roleBindings.putIfAbsent(key("RoleBinding", policyNs, bindingName), binding);
                } else {
                    throw new IllegalArgumentException("Unsupported AccessPolicy target for '" + safeName(policy) + "'");
                }
            }
        }

        List<String> yamls = new ArrayList<>();
        serviceAccounts.values().stream()
                .sorted(Comparator.comparing(sa -> sa.getMetadata().getName()))
                .map(Serialization::asYaml)
                .forEach(yamls::add);
        roles.values().stream()
                .sorted(Comparator.comparing(role -> role.getMetadata().getName()))
                .map(Serialization::asYaml)
                .forEach(yamls::add);
        roleBindings.values().stream()
                .sorted(Comparator.comparing(rb -> rb.getMetadata().getName()))
                .map(Serialization::asYaml)
                .forEach(yamls::add);

        return new Generation(yamls);
    }

    private Target resolveTarget(AccessPolicyDTO policy, LinkDTO link, Map<String, NodeDTO> nodesById) {
        if (policy == null || link == null) {
            return null;
        }
        String targetId = Objects.equals(policy.getId(), link.getSource()) ? normalize(link.getTarget()) : normalize(link.getSource());
        if (!StringUtils.hasText(targetId)) {
            return null;
        }
        NodeDTO targetNode = nodesById.get(targetId);
        if (targetNode == null) {
            throw new IllegalArgumentException("AccessPolicy '" + safeName(policy) + "' references missing target node: " + targetId);
        }

        String kind = (targetNode.getKind() == null ? "" : targetNode.getKind()).toLowerCase(Locale.ROOT);
        if (targetNode instanceof DeploymentDTO || targetNode instanceof JobDTO || targetNode instanceof PodDTO) {
            return new Target(TargetType.WORKLOAD, targetId, safeName(targetNode), targetNode);
        }
        throw new IllegalArgumentException("AccessPolicy '" + safeName(policy) + "' cannot be linked to target kind '" + kind + "'");
    }

    private void applyServiceAccountName(NodeDTO workload, String saName) {
        if (workload instanceof DeploymentDTO dep) {
            dep.setServiceAccountName(saName);
            return;
        }
        if (workload instanceof JobDTO job) {
            job.setServiceAccountName(saName);
            return;
        }
        if (workload instanceof PodDTO pod) {
            pod.setServiceAccountName(saName);
            return;
        }
        throw new IllegalArgumentException("Unsupported workload type for ServiceAccount patching: " + (workload != null ? workload.getKind() : "null"));
    }

    private ServiceAccount buildServiceAccount(String namespace, String name) {
        validateDns1123Label(name, "ServiceAccount");
        return new ServiceAccountBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .endMetadata()
                .build();
    }

    private Role buildRole(AccessPolicyDTO policy, String namespace, String roleName) {
        List<PolicyRule> rules = Optional.ofNullable(policy.getRules()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .map(this::toPolicyRule)
                .toList();

        validateDns1123Label(roleName, "Role");
        return new RoleBuilder()
                .withNewMetadata()
                .withName(roleName)
                .withNamespace(namespace)
                .endMetadata()
                .withRules(rules)
                .build();
    }

    private PolicyRule toPolicyRule(AccessPolicyRuleDTO dto) {
        List<String> resources = Optional.ofNullable(dto.getResources()).orElse(List.of()).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        List<String> verbs = Optional.ofNullable(dto.getVerbs()).orElse(List.of()).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        List<String> apiGroups = Optional.ofNullable(dto.getApiGroups()).orElse(List.of("")).stream()
                .map(value -> value == null ? "" : value.trim())
                .toList();
        List<String> resourceNames = Optional.ofNullable(dto.getResourceNames()).orElse(List.of()).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();

        PolicyRuleBuilder builder = new PolicyRuleBuilder()
                .withApiGroups(apiGroups.isEmpty() ? List.of("") : apiGroups)
                .withResources(resources)
                .withVerbs(verbs);
        if (!resourceNames.isEmpty()) {
            builder.withResourceNames(resourceNames);
        }
        return builder.build();
    }

    private RoleBinding buildRoleBindingForServiceAccount(String namespace, String bindingName, String roleName, String serviceAccountName) {
        validateDns1123Label(bindingName, "RoleBinding");
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
                .withKind("Role")
                .withName(roleName)
                .endRoleRef()
                .build();
    }

    private String roleName(AccessPolicyDTO policy) {
        String base = normalize(policy != null ? policy.getName() : null);
        if (!StringUtils.hasText(base)) {
            throw new IllegalArgumentException("AccessPolicy name is required");
        }
        return sanitizeName(base, 63);
    }

    private String bindingNameForWorkload(AccessPolicyDTO policy, String workloadName, String workloadId) {
        String base = sanitizeName(normalize(policy.getName()), 30) + "-" + sanitizeName(workloadName, 24) + "-rb";
        return ensureMaxLenWithStableSuffix(base, workloadId, 63);
    }

    private String resolveServiceAccountName(AccessPolicyDTO policy, String workloadName, String workloadId, Set<String> usedInPolicy) {
        AccessPolicyBindingStrategy strategy = Optional.ofNullable(policy.getTargetBindingStrategy())
                .orElse(AccessPolicyBindingStrategy.WORKLOAD_SA_PER_WORKLOAD);
        String name;
        switch (strategy) {
            case WORKLOAD_SA_PER_POLICY -> name = sanitizeName(normalize(policy.getName()) + "-sa", 63);
            case WORKLOAD_SA_EXPLICIT_REFERENCE -> {
                String explicit = normalize(policy.getExistingServiceAccountName());
                if (!StringUtils.hasText(explicit)) {
                    throw new IllegalArgumentException("AccessPolicy '" + safeName(policy) + "' requires existingServiceAccountName when using WORKLOAD_SA_EXPLICIT_REFERENCE");
                }
                name = sanitizeName(explicit, 63);
            }
            case WORKLOAD_SA_PER_WORKLOAD -> name = sanitizeName(workloadName + "-sa", 63);
            default -> throw new IllegalArgumentException("Unsupported targetBindingStrategy for AccessPolicy '" + safeName(policy) + "': " + strategy);
        }

        if (!usedInPolicy.contains(name)) {
            return name;
        }
        return ensureMaxLenWithStableSuffix(name, workloadId, 63);
    }

    private void validateRules(AccessPolicyDTO policy) {
        List<AccessPolicyRuleDTO> rules = Optional.ofNullable(policy.getRules()).orElse(List.of());
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("AccessPolicy '" + safeName(policy) + "' must define at least one rule");
        }
        int idx = 0;
        for (AccessPolicyRuleDTO rule : rules) {
            idx++;
            if (rule == null) {
                continue;
            }
            List<String> resources = Optional.ofNullable(rule.getResources()).orElse(List.of()).stream().filter(StringUtils::hasText).toList();
            List<String> verbs = Optional.ofNullable(rule.getVerbs()).orElse(List.of()).stream().filter(StringUtils::hasText).toList();
            if (resources.isEmpty()) {
                throw new IllegalArgumentException("AccessPolicy '" + safeName(policy) + "' rule #" + idx + " must include at least one resource");
            }
            if (verbs.isEmpty()) {
                throw new IllegalArgumentException("AccessPolicy '" + safeName(policy) + "' rule #" + idx + " must include at least one verb");
            }
        }
    }

    private String resolveNamespace(AccessPolicyDTO policy, String defaultNamespace) {
        String ns = policy != null ? policy.getNamespace() : null;
        return StringUtils.hasText(ns) ? ns.trim() : defaultNamespace;
    }

    private String safeName(NodeDTO node) {
        String name = node != null ? node.getName() : null;
        return StringUtils.hasText(name) ? name.trim() : Optional.ofNullable(node != null ? node.getId() : null).orElse("unknown");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String key(String kind, String namespace, String name) {
        return kind + ":" + namespace + ":" + name;
    }

    private String sanitizeName(String raw, int maxLen) {
        String value = normalize(raw).toLowerCase(Locale.ROOT);
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

    private String ensureMaxLenWithStableSuffix(String base, String entropy, int maxLen) {
        String normalizedBase = sanitizeName(base, maxLen);
        if (normalizedBase.length() <= maxLen) {
            return normalizedBase;
        }
        String suffix = shortHash(entropy);
        int available = Math.max(1, maxLen - (suffix.length() + 1));
        String prefix = sanitizeName(normalizedBase, available);
        return prefix + "-" + suffix;
    }

    private String shortHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(String.valueOf(input).getBytes(StandardCharsets.UTF_8));
            String hex = toHex(bytes);
            return hex.substring(0, 6);
        } catch (Exception ex) {
            return "000000";
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void validateDns1123Label(String name, String resourceType) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException(resourceType + " name is required");
        }
        if (name.length() > 63) {
            throw new IllegalArgumentException(resourceType + " name must be <= 63 characters");
        }
        if (!name.matches("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")) {
            throw new IllegalArgumentException(resourceType + " name must be a valid DNS-1123 label (lowercase alphanumeric, '-', start/end alphanumeric)");
        }
    }

    private record Target(TargetType type, String id, String displayName, NodeDTO node) {}

    private enum TargetType {
        WORKLOAD("1-workload");

        private final String sortKey;

        TargetType(String sortKey) {
            this.sortKey = sortKey;
        }
    }
}
