package com.izylife.izykube.services;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class CrdDerivationService {

    public String derivePlural(String singularName) {
        String singular = normalize(singularName);
        if (singular == null) {
            return null;
        }
        return singular + "s";
    }

    public String deriveKind(String singularName) {
        String singular = normalize(singularName);
        if (singular == null) {
            return null;
        }
        String[] parts = singular.split("[\\W_]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    public String deriveMetadataName(String plural, String group) {
        String p = normalize(plural);
        String g = normalize(group);
        if (p == null || g == null) {
            return null;
        }
        return p + "." + g;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

