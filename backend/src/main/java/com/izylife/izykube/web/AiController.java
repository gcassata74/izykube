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

package com.izylife.izykube.web;

import com.izylife.izykube.dto.ai.AiChatMessage;
import com.izylife.izykube.dto.ai.AiChatRequest;
import com.izylife.izykube.dto.ai.AiChatResponse;
import com.izylife.izykube.dto.ai.AiGenerateRequest;
import com.izylife.izykube.dto.ai.AiGenerateResponse;
import com.izylife.izykube.dto.ai.AiImportRequest;
import com.izylife.izykube.dto.ai.AiExportResponse;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.services.ai.LocalAiService;
import com.izylife.izykube.services.ai.LocalAiService.LocalAiServiceException;
import com.izylife.izykube.services.ai.ClusterYamlService;
import com.izylife.izykube.services.ai.ClusterYamlException;
import com.izylife.izykube.services.ai.HelmChartArchive;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final LocalAiService localAiService;
    private final ClusterYamlService clusterYamlService;

    public AiController(LocalAiService localAiService, ClusterYamlService clusterYamlService) {
        this.localAiService = localAiService;
        this.clusterYamlService = clusterYamlService;
    }

    @PostMapping("/generate")
    public ResponseEntity<AiGenerateResponse> generate(@Valid @RequestBody AiGenerateRequest request) {
        var result = localAiService.generateCompletion(request);
        var response = new AiGenerateResponse(
                result.getContent(),
                request.getTask(),
                request.getFormat(),
                result.getModel()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/import-yaml")
    public ResponseEntity<ClusterDTO> importYaml(@Valid @RequestBody AiImportRequest request) {
        ClusterDTO cluster = clusterYamlService.importCluster(request.getYaml(), request.getName());
        return ResponseEntity.ok(cluster);
    }

    @PostMapping("/export-yaml")
    public ResponseEntity<?> exportYaml(@RequestBody ClusterDTO cluster) {
        String exportMode = Optional.ofNullable(cluster.getExportMode()).orElse("FLAT_YAML");
        if ("HELM_CHART".equalsIgnoreCase(exportMode)) {
            HelmChartArchive archive = clusterYamlService.exportHelmChart(cluster);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archive.fileName() + "\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(archive.content());
        }
        String yaml = clusterYamlService.exportCluster(cluster);
        return ResponseEntity.ok(new AiExportResponse(yaml));
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        var result = localAiService.chat(request);
        var assistantMessage = new AiChatMessage("assistant", result.getContent());
        var response = new AiChatResponse(
                List.of(assistantMessage),
                result.getModel(),
                request.getTask()
        );
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(LocalAiServiceException.class)
    public ResponseEntity<String> handleLocalAi(LocalAiServiceException ex) {
        log.error("Local AI error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
    }

    @ExceptionHandler(ClusterYamlException.class)
    public ResponseEntity<String> handleInvalidYaml(ClusterYamlException ex) {
        log.warn("Invalid cluster YAML import: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
