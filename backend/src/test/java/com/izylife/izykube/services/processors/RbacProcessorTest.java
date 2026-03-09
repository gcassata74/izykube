package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.AccessPolicyBindingStrategy;
import com.izylife.izykube.dto.cluster.AccessPolicyDTO;
import com.izylife.izykube.dto.cluster.AccessPolicyRuleDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.ServiceAccountDTO;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RbacProcessorTest {

    @Test
    void workloadBindingGeneratesServiceAccountRoleRoleBindingAndPatchesWorkload() {
        RbacProcessor processor = new RbacProcessor();

        AccessPolicyDTO policy = policy("ap-1", "my-app-reader", "demo");
        DeploymentDTO deployment = new DeploymentDTO("dep-1", "my-app", 1, "RollingUpdate", "", 80);

        LinkDTO link = link(policy.getId(), deployment.getId(), "appliesTo");

        RbacProcessor.Generation generation = processor.generateAndApply("demo", List.of(policy, deployment), List.of(link));

        assertEquals("my-app-sa", deployment.getServiceAccountName());

        Map<String, Map<String, Object>> docs = toDocsByKindAndName(generation.yamls());
        assertTrue(docs.containsKey("ServiceAccount/demo/my-app-sa"));
        assertTrue(docs.containsKey("Role/demo/my-app-reader"));
        assertTrue(docs.containsKey("RoleBinding/demo/my-app-reader-my-app-rb"));

        Map<String, Object> roleBinding = docs.get("RoleBinding/demo/my-app-reader-my-app-rb");
        Map<String, Object> roleRef = castMap(roleBinding.get("roleRef"));
        assertEquals("Role", roleRef.get("kind"));
        assertEquals("my-app-reader", roleRef.get("name"));
        List<Map<String, Object>> subjects = castList(roleBinding.get("subjects"));
        assertEquals("ServiceAccount", subjects.get(0).get("kind"));
        assertEquals("my-app-sa", subjects.get(0).get("name"));
        assertEquals("demo", subjects.get(0).get("namespace"));
    }

    @Test
    void perPolicyStrategyUsesSingleServiceAccountName() {
        RbacProcessor processor = new RbacProcessor();

        AccessPolicyDTO policy = policy("ap-1", "writer", "demo");
        policy.setTargetBindingStrategy(AccessPolicyBindingStrategy.WORKLOAD_SA_PER_POLICY);
        DeploymentDTO a = new DeploymentDTO("dep-a", "a", 1, "RollingUpdate", "", 80);
        DeploymentDTO b = new DeploymentDTO("dep-b", "b", 1, "RollingUpdate", "", 80);

        RbacProcessor.Generation generation = processor.generateAndApply("demo", List.of(policy, a, b), List.of(
                link(policy.getId(), a.getId(), "appliesTo"),
                link(policy.getId(), b.getId(), "appliesTo")
        ));

        assertEquals("writer-sa", a.getServiceAccountName());
        assertEquals("writer-sa", b.getServiceAccountName());

        Map<String, Map<String, Object>> docs = toDocsByKindAndName(generation.yamls());
        long saCount = docs.keySet().stream().filter(key -> key.startsWith("ServiceAccount/demo/")).count();
        assertEquals(1, saCount);
    }

    @Test
    void explicitServiceAccountMissingThrowsValidationError() {
        RbacProcessor processor = new RbacProcessor();

        AccessPolicyDTO policy = policy("ap-1", "reader", "demo");
        policy.setTargetBindingStrategy(AccessPolicyBindingStrategy.WORKLOAD_SA_EXPLICIT_REFERENCE);
        DeploymentDTO deployment = new DeploymentDTO("dep-1", "my-app", 1, "RollingUpdate", "", 80);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                processor.generateAndApply("demo", List.of(policy, deployment), List.of(link(policy.getId(), deployment.getId(), "appliesTo"))));
        assertTrue(ex.getMessage().contains("existingServiceAccountName"));
    }

    @Test
    void idempotentGenerationDoesNotDuplicateResources() {
        RbacProcessor processor = new RbacProcessor();

        AccessPolicyDTO policy = policy("ap-1", "my-app-reader", "demo");
        DeploymentDTO deployment = new DeploymentDTO("dep-1", "my-app", 1, "RollingUpdate", "", 80);
        LinkDTO link = link(policy.getId(), deployment.getId(), "appliesTo");

        RbacProcessor.Generation first = processor.generateAndApply("demo", List.of(policy, deployment), List.of(link));
        RbacProcessor.Generation second = processor.generateAndApply("demo", List.of(policy, deployment), List.of(link));

        assertEquals(first.yamls().size(), second.yamls().size());
        assertEquals(toDocsByKindAndName(first.yamls()).keySet(), toDocsByKindAndName(second.yamls()).keySet());
    }

    @Test
    void onePolicyTwoWorkloadsGeneratesOneRoleTwoBindingsAndTwoServiceAccounts() {
        RbacProcessor processor = new RbacProcessor();

        AccessPolicyDTO policy = policy("ap-1", "my-app-reader", "demo");
        DeploymentDTO a = new DeploymentDTO("dep-a", "alpha", 1, "RollingUpdate", "", 80);
        DeploymentDTO b = new DeploymentDTO("dep-b", "beta", 1, "RollingUpdate", "", 80);

        RbacProcessor.Generation generation = processor.generateAndApply("demo", List.of(policy, a, b), List.of(
                link(policy.getId(), a.getId(), "appliesTo"),
                link(policy.getId(), b.getId(), "appliesTo")
        ));

        Map<String, Map<String, Object>> docs = toDocsByKindAndName(generation.yamls());
        long roleCount = docs.keySet().stream().filter(key -> key.startsWith("Role/demo/")).count();
        long bindingCount = docs.keySet().stream().filter(key -> key.startsWith("RoleBinding/demo/")).count();
        long saCount = docs.keySet().stream().filter(key -> key.startsWith("ServiceAccount/demo/")).count();
        assertEquals(1, roleCount);
        assertEquals(2, bindingCount);
        assertEquals(2, saCount);
        assertEquals("alpha-sa", a.getServiceAccountName());
        assertEquals("beta-sa", b.getServiceAccountName());
    }

    @Test
    void nameSanitizationProducesDnsLabels() {
        RbacProcessor processor = new RbacProcessor();

        AccessPolicyDTO policy = policy("ap-1", "My App Reader", "demo");
        DeploymentDTO deployment = new DeploymentDTO("dep-1", "My App", 1, "RollingUpdate", "", 80);

        RbacProcessor.Generation generation = processor.generateAndApply("demo", List.of(policy, deployment), List.of(link(policy.getId(), deployment.getId(), "appliesTo")));
        Map<String, Map<String, Object>> docs = toDocsByKindAndName(generation.yamls());

        assertTrue(docs.containsKey("Role/demo/my-app-reader"));
        assertTrue(docs.containsKey("ServiceAccount/demo/my-app-sa"));
        assertTrue(docs.keySet().stream().allMatch(key -> key.matches("^(ServiceAccount|Role|RoleBinding)/demo/[a-z0-9]([a-z0-9-]*[a-z0-9])?$")));
    }

    @Test
    void clusterRoleAndClusterRoleBindingAreGeneratedWhenSelected() {
        RbacProcessor processor = new RbacProcessor();

        AccessPolicyDTO policy = policy("ap-1", "global-reader", "demo");
        policy.setRoleKind("ClusterRole");
        policy.setBindingKind("ClusterRoleBinding");
        DeploymentDTO deployment = new DeploymentDTO("dep-1", "my-app", 1, "RollingUpdate", "", 80);

        RbacProcessor.Generation generation = processor.generateAndApply("demo", List.of(policy, deployment), List.of(link(policy.getId(), deployment.getId(), "appliesTo")));
        Map<String, Map<String, Object>> docs = toDocsByKindAndName(generation.yamls());

        assertTrue(docs.containsKey("ClusterRole/null/global-reader"));
        assertTrue(docs.containsKey("ClusterRoleBinding/null/global-reader-my-app-rb"));
    }

    @Test
    void clusterRoleBindingWithRoleThrowsValidationError() {
        RbacProcessor processor = new RbacProcessor();

        AccessPolicyDTO policy = policy("ap-1", "reader", "demo");
        policy.setRoleKind("Role");
        policy.setBindingKind("ClusterRoleBinding");
        DeploymentDTO deployment = new DeploymentDTO("dep-1", "my-app", 1, "RollingUpdate", "", 80);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                processor.generateAndApply("demo", List.of(policy, deployment), List.of(link(policy.getId(), deployment.getId(), "appliesTo"))));
        assertTrue(ex.getMessage().contains("ClusterRoleBinding"));
    }

    @Test
    void roleBindingNodeDerivesRoleAndServiceAccountFromLinks() {
        RbacProcessor processor = new RbacProcessor();

        AccessPolicyDTO role = policy("ap-role", "reader", "demo");
        ServiceAccountDTO serviceAccount = new ServiceAccountDTO("sa-1", "workload-sa");
        serviceAccount.setNamespace("demo");
        DeploymentDTO deployment = new DeploymentDTO("dep-1", "my-app", 1, "RollingUpdate", "", 80);
        AccessPolicyDTO roleBinding = new AccessPolicyDTO("ap-rb", "reader-binding");
        roleBinding.setNamespace("demo");
        roleBinding.setRbacNodeType("ROLEBINDING");
        roleBinding.setRules(List.of());

        RbacProcessor.Generation generation = processor.generateAndApply(
                "demo",
                List.of(role, serviceAccount, deployment, roleBinding),
                List.of(
                        link(role.getId(), deployment.getId(), "appliesTo"),
                        link(roleBinding.getId(), role.getId(), "appliesTo"),
                        link(roleBinding.getId(), serviceAccount.getId(), "appliesTo")
                )
        );

        Map<String, Map<String, Object>> docs = toDocsByKindAndName(generation.yamls());
        assertTrue(docs.containsKey("Role/demo/reader"));
        assertTrue(docs.containsKey("RoleBinding/demo/reader-binding-rb"));

        Map<String, Object> roleBindingDoc = docs.get("RoleBinding/demo/reader-binding-rb");
        Map<String, Object> roleRef = castMap(roleBindingDoc.get("roleRef"));
        assertEquals("Role", roleRef.get("kind"));
        assertEquals("reader", roleRef.get("name"));
        List<Map<String, Object>> subjects = castList(roleBindingDoc.get("subjects"));
        assertEquals("workload-sa", subjects.get(0).get("name"));
    }

    private AccessPolicyDTO policy(String id, String name, String namespace) {
        AccessPolicyDTO policy = new AccessPolicyDTO(id, name);
        policy.setNamespace(namespace);

        AccessPolicyRuleDTO rule = new AccessPolicyRuleDTO();
        rule.setApiGroups(List.of(""));
        rule.setResources(List.of("pods"));
        rule.setVerbs(List.of("get", "list"));
        policy.setRules(List.of(rule));
        return policy;
    }

    private LinkDTO link(String source, String target, String type) {
        LinkDTO link = new LinkDTO();
        link.setSource(source);
        link.setTarget(target);
        link.setType(type);
        return link;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> toDocsByKindAndName(List<String> yamls) {
        Map<String, Map<String, Object>> out = new java.util.LinkedHashMap<>();
        Yaml parser = new Yaml();
        for (String yaml : yamls) {
            Map<String, Object> doc = (Map<String, Object>) parser.load(yaml);
            String kind = String.valueOf(doc.get("kind"));
            Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
            String name = String.valueOf(metadata.get("name"));
            String ns = String.valueOf(metadata.get("namespace"));
            out.put(kind + "/" + ns + "/" + name, doc);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        assertNotNull(value);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        assertNotNull(value);
        return (List<Map<String, Object>>) value;
    }
}
