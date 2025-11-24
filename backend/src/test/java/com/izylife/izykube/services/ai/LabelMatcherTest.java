package com.izylife.izykube.services.ai;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelMatcherTest {

    @Test
    void matchesReturnsTrueWhenSelectorIsSubset() {
        Map<String, Object> selector = Map.of(
                "app", "web",
                "tier", "backend"
        );
        Map<String, Object> labels = Map.of(
                "app", "web",
                "tier", "backend",
                "track", "stable"
        );

        assertTrue(LabelMatcher.matches(selector, labels));
    }

    @Test
    void matchesReturnsFalseWhenLabelMissing() {
        Map<String, Object> selector = Map.of("app", "web", "tier", "backend");
        Map<String, Object> labels = Map.of("app", "web");

        assertFalse(LabelMatcher.matches(selector, labels));
    }

    @Test
    void matchesTreatsValuesAsStrings() {
        Map<String, Object> selector = Map.of("generation", 1);
        Map<String, Object> labels = Map.of("generation", "1 ");

        assertTrue(LabelMatcher.matches(selector, labels));
    }

    @Test
    void normalizeDropsBlankEntries() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("app", "web");
        raw.put("tier", " ");
        raw.put("", "ignored");
        raw.put("nullValue", null);

        Map<String, String> normalized = LabelMatcher.normalize(raw);

        assertEquals(1, normalized.size());
        assertEquals("web", normalized.get("app"));
    }
}
