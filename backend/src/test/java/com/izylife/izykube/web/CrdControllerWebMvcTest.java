package com.izylife.izykube.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.izykube.dto.crd.CrdDefinitionRequestDTO;
import com.izylife.izykube.dto.crd.CrdDefinitionResponseDTO;
import com.izylife.izykube.dto.crd.CrdDefinitionSummaryResponseDTO;
import com.izylife.izykube.services.CrdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CrdControllerWebMvcTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CrdService crdService;

    @BeforeEach
    void setUp() {
        crdService = mock(CrdService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CrdController(crdService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void shouldCreateListAndUpdate() throws Exception {
        CrdDefinitionRequestDTO createRequest = new CrdDefinitionRequestDTO();
        createRequest.setGroup("example.com");
        createRequest.setSingularName("widget");
        createRequest.setScope("Namespaced");
        createRequest.setVersion("v1");

        CrdDefinitionResponseDTO created = new CrdDefinitionResponseDTO();
        created.setId("crd-1");
        created.setGroup("example.com");
        created.setSingularName("widget");
        created.setScope("Namespaced");
        created.setVersion("v1");
        created.setPlural("widgets");
        created.setKind("Widget");
        created.setMetadataName("widgets.example.com");

        CrdDefinitionSummaryResponseDTO summary = new CrdDefinitionSummaryResponseDTO();
        summary.setId("crd-1");
        summary.setGroup("example.com");
        summary.setSingularName("widget");
        summary.setScope("Namespaced");
        summary.setVersion("v1");
        summary.setMetadataName("widgets.example.com");

        CrdDefinitionRequestDTO updateRequest = new CrdDefinitionRequestDTO();
        updateRequest.setGroup("example.com");
        updateRequest.setSingularName("widget");
        updateRequest.setScope("Namespaced");
        updateRequest.setVersion("v1");

        CrdDefinitionResponseDTO updated = new CrdDefinitionResponseDTO();
        updated.setId("crd-1");
        updated.setGroup("example.com");

        when(crdService.create(ArgumentMatchers.any(CrdDefinitionRequestDTO.class))).thenReturn(created);
        when(crdService.list()).thenReturn(List.of(summary));
        when(crdService.update(ArgumentMatchers.eq("crd-1"), ArgumentMatchers.any(CrdDefinitionRequestDTO.class))).thenReturn(updated);

        mockMvc.perform(post("/api/crds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("crd-1"));

        mockMvc.perform(get("/api/crds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("crd-1"))
                .andExpect(jsonPath("$[0].metadataName").value("widgets.example.com"));

        mockMvc.perform(put("/api/crds/crd-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("crd-1"));
    }
}
