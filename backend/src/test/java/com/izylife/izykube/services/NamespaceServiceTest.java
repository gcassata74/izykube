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
