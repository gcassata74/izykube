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

