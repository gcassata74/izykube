/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.izylife.izykube.services.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Utility methods for working with Kubernetes-style label selectors.
 * The helper keeps comparisons small and testable so importer logic can focus on graph building.
 */
public final class LabelMatcher {

    private LabelMatcher() {
        // utility
    }

    /**
     * Normalizes a label map by trimming keys/values, converting values to strings
     * and discarding blank or null entries.
     *
     * @param labels raw labels or selectors
     * @return a new map containing normalized label pairs
     */
    public static Map<String, String> normalize(Map<String, ?> labels) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (labels == null || labels.isEmpty()) {
            return normalized;
        }
        labels.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            String normalizedKey = key.trim();
            if (normalizedKey.isEmpty()) {
                return;
            }
            String normalizedValue = Objects.toString(value, "").trim();
            if (normalizedValue.isEmpty()) {
                return;
            }
            normalized.put(normalizedKey, normalizedValue);
        });
        return normalized;
    }

    /**
     * Compares two arbitrary label maps after normalizing their values.
     *
     * @param selector selector to evaluate
     * @param labels   candidate labels
     * @return true when selector is a subset of labels
     */
    public static boolean matches(Map<String, ?> selector, Map<String, ?> labels) {
        return matchesNormalized(normalize(selector), normalize(labels));
    }

    /**
     * Compares two already-normalized label maps.
     *
     * @param selector normalized selector map
     * @param labels   normalized label map
     * @return true when selector entries are contained inside labels
     */
    public static boolean matchesNormalized(Map<String, String> selector, Map<String, String> labels) {
        if (selector == null || selector.isEmpty()) {
            return false;
        }
        if (labels == null || labels.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> entry : selector.entrySet()) {
            String key = entry.getKey();
            String expectedValue = entry.getValue();
            String actualValue = labels.get(key);
            if (!Objects.equals(expectedValue, actualValue)) {
                return false;
            }
        }
        return true;
    }
}
