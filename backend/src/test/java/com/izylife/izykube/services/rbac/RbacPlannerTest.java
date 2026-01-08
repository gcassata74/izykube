package com.izylife.izykube.services.rbac;

import com.izylife.izykube.services.rbac.RbacPlanner.Edge;
import com.izylife.izykube.services.rbac.RbacPlanner.Graph;
import com.izylife.izykube.services.rbac.RbacPlanner.Options;
import com.izylife.izykube.services.rbac.RbacPlanner.RbacBlockNode;
import com.izylife.izykube.services.rbac.RbacPlanner.RbacKind;
import com.izylife.izykube.services.rbac.RbacPlanner.RbacPlan;
import com.izylife.izykube.services.rbac.RbacPlanner.WorkloadKind;
import com.izylife.izykube.services.rbac.RbacPlanner.WorkloadNode;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RbacPlannerTest {

    @Test
    void oneRbacBlockOneWorkloadDedicatedServiceAccount() {
        WorkloadNode web = workload("w1", WorkloadKind.DEPLOYMENT, "web", "demo", Map.of());
        RbacBlockNode reader = rbac("r1", RbacKind.ROLE, "reader", "demo");
        Edge link = edge("e1", web.id(), reader.id());

        RbacPlan plan = RbacPlanner.buildRbacPlan(new Graph(List.of(web), List.of(reader), List.of(link)), new Options(false));

        assertTrue(plan.errors().isEmpty());
        assertTrue(plan.warnings().isEmpty());
        assertEquals(1, plan.workloadPatches().size());
        assertEquals("web-sa", extractPatchedServiceAccount(plan.workloadPatches().get(0).mergePatch()));

        Map<String, Map<String, Object>> docs = docsByKindNameNamespace(plan.resources());
        assertTrue(docs.containsKey("ServiceAccount/demo/web-sa"));
        assertTrue(docs.keySet().stream().anyMatch(key -> key.startsWith("RoleBinding/demo/")));
    }

    @Test
    void oneRbacBlockTwoWorkloadsSharedServiceAccount() {
        WorkloadNode a = workload("w1", WorkloadKind.DEPLOYMENT, "a", "demo", Map.of());
        WorkloadNode b = workload("w2", WorkloadKind.DEPLOYMENT, "b", "demo", Map.of());
        RbacBlockNode reader = rbac("r1", RbacKind.ROLE, "reader", "demo");

        RbacPlan plan = RbacPlanner.buildRbacPlan(
                new Graph(
                        List.of(a, b),
                        List.of(reader),
                        List.of(edge("e1", a.id(), reader.id()), edge("e2", b.id(), reader.id()))
                ),
                new Options(false)
        );

        assertTrue(plan.errors().isEmpty());

        String saA = extractPatchedServiceAccount(plan.workloadPatches().get(0).mergePatch());
        String saB = extractPatchedServiceAccount(plan.workloadPatches().get(1).mergePatch());
        assertEquals("reader-sa", saA);
        assertEquals("reader-sa", saB);

        Map<String, Map<String, Object>> docs = docsByKindNameNamespace(plan.resources());
        assertTrue(docs.containsKey("ServiceAccount/demo/reader-sa"));

        long bindingCount = docs.keySet().stream().filter(key -> key.startsWith("RoleBinding/demo/")).count();
        assertEquals(2, bindingCount);
    }

    @Test
    void oneWorkloadTwoRbacBlocksDedicatedOneServiceAccountTwoBindings() {
        WorkloadNode app = workload("w1", WorkloadKind.DEPLOYMENT, "app", "demo", Map.of());
        RbacBlockNode a = rbac("r1", RbacKind.ROLE, "a", "demo");
        RbacBlockNode b = rbac("r2", RbacKind.CLUSTER_ROLE, "b", null);

        RbacPlan plan = RbacPlanner.buildRbacPlan(
                new Graph(
                        List.of(app),
                        List.of(a, b),
                        List.of(edge("e1", app.id(), a.id()), edge("e2", app.id(), b.id()))
                ),
                new Options(false)
        );

        assertTrue(plan.errors().isEmpty());
        assertEquals(1, plan.workloadPatches().size());
        assertEquals("app-sa", extractPatchedServiceAccount(plan.workloadPatches().get(0).mergePatch()));

        Map<String, Map<String, Object>> docs = docsByKindNameNamespace(plan.resources());
        assertTrue(docs.containsKey("ServiceAccount/demo/app-sa"));

        List<Map<String, Object>> roleBindings = docs.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("RoleBinding/demo/"))
                .map(Map.Entry::getValue)
                .toList();
        assertEquals(2, roleBindings.size());

        // One binding must be RoleRef(Role), one must be RoleRef(ClusterRole).
        boolean sawRole = false;
        boolean sawClusterRole = false;
        for (Map<String, Object> rbDoc : roleBindings) {
            Map<String, Object> roleRef = castMap(rbDoc.get("roleRef"));
            if ("Role".equals(roleRef.get("kind"))) {
                sawRole = true;
            }
            if ("ClusterRole".equals(roleRef.get("kind"))) {
                sawClusterRole = true;
            }
        }
        assertTrue(sawRole);
        assertTrue(sawClusterRole);
    }

    @Test
    void mixedScenarioForcesDedicatedWhenAnyWorkloadInGroupHasMultipleBlocks() {
        WorkloadNode w1 = workload("w1", WorkloadKind.DEPLOYMENT, "w1", "demo", Map.of());
        WorkloadNode w2 = workload("w2", WorkloadKind.DEPLOYMENT, "w2", "demo", Map.of());
        RbacBlockNode shared = rbac("r1", RbacKind.ROLE, "shared", "demo");
        RbacBlockNode extra = rbac("r2", RbacKind.ROLE, "extra", "demo");

        // Group (demo, Role, shared) has 2 workloads but w1 has 2 RBAC blocks total -> must force dedicated.
        RbacPlan plan = RbacPlanner.buildRbacPlan(
                new Graph(
                        List.of(w1, w2),
                        List.of(shared, extra),
                        List.of(
                                edge("e1", w1.id(), shared.id()),
                                edge("e2", w2.id(), shared.id()),
                                edge("e3", w1.id(), extra.id())
                        )
                ),
                new Options(false)
        );

        assertTrue(plan.errors().isEmpty());

        Map<String, String> workloadToSa = plan.workloadPatches().stream()
                .collect(java.util.stream.Collectors.toMap(
                        patch -> patch.workloadId(),
                        patch -> extractPatchedServiceAccount(patch.mergePatch())
                ));

        assertEquals("w1-sa", workloadToSa.get("w1"));
        assertEquals("w2-sa", workloadToSa.get("w2"));

        Map<String, Map<String, Object>> docs = docsByKindNameNamespace(plan.resources());
        assertTrue(docs.containsKey("ServiceAccount/demo/w1-sa"));
        assertTrue(docs.containsKey("ServiceAccount/demo/w2-sa"));
        assertFalse(docs.containsKey("ServiceAccount/demo/shared-sa"));
    }

    private WorkloadNode workload(String id, WorkloadKind kind, String name, String namespace, Map<String, Object> spec) {
        return new WorkloadNode(id, kind, name, namespace, spec);
    }

    private RbacBlockNode rbac(String id, RbacKind kind, String name, String namespace) {
        return new RbacBlockNode(id, kind, name, namespace);
    }

    private Edge edge(String id, String fromWorkloadId, String toRbacBlockId) {
        return new Edge(id, "RBAC_LINK", fromWorkloadId, toRbacBlockId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> docsByKindNameNamespace(List<String> yamls) {
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
    private String extractPatchedServiceAccount(Map<String, Object> patch) {
        Map<String, Object> spec = (Map<String, Object>) patch.get("spec");
        assertNotNull(spec);
        if (spec.containsKey("jobTemplate")) {
            Map<String, Object> jobTemplate = (Map<String, Object>) spec.get("jobTemplate");
            Map<String, Object> jtSpec = (Map<String, Object>) jobTemplate.get("spec");
            Map<String, Object> template = (Map<String, Object>) jtSpec.get("template");
            Map<String, Object> podSpec = (Map<String, Object>) template.get("spec");
            return String.valueOf(podSpec.get("serviceAccountName"));
        }
        Map<String, Object> template = (Map<String, Object>) spec.get("template");
        Map<String, Object> podSpec = (Map<String, Object>) template.get("spec");
        return String.valueOf(podSpec.get("serviceAccountName"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        assertNotNull(value);
        return (Map<String, Object>) value;
    }
}

