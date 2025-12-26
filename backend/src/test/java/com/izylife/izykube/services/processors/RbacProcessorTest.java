package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.ServiceAccountDTO;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RbacProcessorTest {

    @Test
    void generatesRoleBindingToStandardClusterRole() {
        RbacProcessor processor = new RbacProcessor();

        ServiceAccountDTO sa = new ServiceAccountDTO("sa-1", "example-sa");
        sa.setNamespace("demo");
        sa.setRbacProfile("VIEW");

        List<String> yamls = processor.createTemplates("demo", List.of(sa));
        assertEquals(1, yamls.size());

        Map<String, Object> doc = castMap(new Yaml().load(yamls.get(0)));
        assertEquals("RoleBinding", doc.get("kind"));
        Map<String, Object> roleRef = castMap(doc.get("roleRef"));
        assertEquals("ClusterRole", roleRef.get("kind"));
        assertEquals("view", roleRef.get("name"));

        List<Map<String, Object>> subjects = castList(doc.get("subjects"));
        assertTrue(subjects.size() >= 1);
        assertEquals("ServiceAccount", subjects.get(0).get("kind"));
        assertEquals("example-sa", subjects.get(0).get("name"));
        assertEquals("demo", subjects.get(0).get("namespace"));
    }

    @Test
    void noneProfileProducesNoRoleBinding() {
        RbacProcessor processor = new RbacProcessor();

        ServiceAccountDTO sa = new ServiceAccountDTO("sa-1", "example-sa");
        sa.setNamespace("demo");
        sa.setRbacProfile("NONE");

        List<String> yamls = processor.createTemplates("demo", List.of(sa));
        assertEquals(0, yamls.size());
    }

    @Test
    void duplicateServiceAccountNamesAreRejected() {
        RbacProcessor processor = new RbacProcessor();

        ServiceAccountDTO saA = new ServiceAccountDTO("sa-1", "example-sa");
        saA.setNamespace("demo");
        saA.setRbacProfile("VIEW");

        ServiceAccountDTO saB = new ServiceAccountDTO("sa-2", "example-sa");
        saB.setNamespace("demo");
        saB.setRbacProfile("VIEW");

        assertThrows(IllegalArgumentException.class, () -> processor.createTemplates("demo", List.of(saA, saB)));
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
