package com.izylife.izykube.services.processors;

import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

final class YamlKeyValueExtractor {

    private YamlKeyValueExtractor() {
    }

    static Map<String, String> extractPlainKeyValueData(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return Map.of();
        }

        Yaml yamlParser = new Yaml();
        Object loaded = yamlParser.load(yamlContent);
        Map<String, Object> source = resolveKeyValueSource(loaded);

        Map<String, String> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> normalized.put(String.valueOf(key), normalizeValue(value)));
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveKeyValueSource(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Invalid YAML content provided");
        }

        if (map.containsKey("data")) {
            Object dataSection = map.get("data");
            if (dataSection instanceof Map<?, ?> dataMap) {
                return new LinkedHashMap<>((Map<String, Object>) dataMap);
            }
        }

        if (map.containsKey("stringData")) {
            Object stringData = map.get("stringData");
            if (stringData instanceof Map<?, ?> dataMap) {
                return new LinkedHashMap<>((Map<String, Object>) dataMap);
            }
        }

        if (map.containsKey("kind")) {
            return new LinkedHashMap<>();
        }

        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    private static String normalizeValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String str) {
            return str;
        }
        String dumped = new Yaml().dump(value);
        return dumped == null ? "" : dumped.trim();
    }
}
