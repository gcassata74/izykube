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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Sort;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NamespaceServiceTest {

    @Mock
    private NamespaceRepository namespaceRepository;

    private NamespaceService namespaceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        namespaceService = new NamespaceService(namespaceRepository);
    }

    @Test
    void ensureNamespaceExistsCreatesWhenMissing() {
        when(namespaceRepository.findByNameIgnoreCase("production")).thenReturn(Optional.empty());
        when(namespaceRepository.save(any(Namespace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Namespace namespace = namespaceService.ensureNamespaceExists("production");

        verify(namespaceRepository).save(any(Namespace.class));
        assertEquals("production", namespace.getName());
    }

    @Test
    void createNamespaceReturnsExisting() {
        Namespace existing = new Namespace();
        existing.setName("default");
        when(namespaceRepository.findByNameIgnoreCase("default")).thenReturn(Optional.of(existing));

        Namespace namespace = namespaceService.createNamespace("default", "core");

        verify(namespaceRepository, never()).save(any());
        assertEquals("default", namespace.getName());
    }
}
