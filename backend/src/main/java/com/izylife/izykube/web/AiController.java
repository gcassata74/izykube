package com.izylife.izykube.web;

import com.izylife.izykube.dto.ai.AiChatMessage;
import com.izylife.izykube.dto.ai.AiChatRequest;
import com.izylife.izykube.dto.ai.AiChatResponse;
import com.izylife.izykube.dto.ai.AiGenerateRequest;
import com.izylife.izykube.dto.ai.AiGenerateResponse;
import com.izylife.izykube.dto.ai.AiImportRequest;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.services.ai.LocalAiService;
import com.izylife.izykube.services.ai.LocalAiService.LocalAiServiceException;
import com.izylife.izykube.services.ai.YamlImportService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final LocalAiService localAiService;
    private final YamlImportService yamlImportService;

    public AiController(LocalAiService localAiService, YamlImportService yamlImportService) {
        this.localAiService = localAiService;
        this.yamlImportService = yamlImportService;
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
        ClusterDTO cluster = yamlImportService.importCluster(request.getYaml(), request.getName());
        return ResponseEntity.ok(cluster);
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
}
