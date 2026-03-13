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

import com.izylife.izykube.model.Namespace;
import com.izylife.izykube.repositories.NamespaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NamespaceService {

    private final NamespaceRepository namespaceRepository;

    public List<Namespace> listNamespaces() {
        return namespaceRepository.findAllSorted();
    }

    public Namespace createNamespace(String name, String description) {
        String normalized = normalizeName(name);
        return namespaceRepository.findByNameIgnoreCase(normalized)
                .map(existing -> {
                    if (description != null) {
                        existing.setDescription(description);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    Namespace namespace = new Namespace();
                    namespace.setName(normalized);
                    if (description != null) {
                        namespace.setDescription(description);
                    }
                    return namespaceRepository.save(namespace);
                });
    }

    public Namespace ensureNamespaceExists(String name) {
        String normalized = normalizeName(name);
        return namespaceRepository.findByNameIgnoreCase(normalized)
                .orElseGet(() -> {
                    Namespace namespace = new Namespace();
                    namespace.setName(normalized);
                    namespace.setDescription("Auto-created namespace");
                    return namespaceRepository.save(namespace);
                });
    }

    public void deleteNamespaceRecord(String name) {
        String normalized = normalizeName(name);
        if ("default".equalsIgnoreCase(normalized)) {
            return;
        }
        namespaceRepository.findByNameIgnoreCase(normalized)
                .ifPresent(namespaceRepository::delete);
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            return "default";
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
