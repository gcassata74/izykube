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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.izylife.izykube.dto.ai.AiChatRequest;
import com.izylife.izykube.dto.ai.AiGenerateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LocalAiService {

    private static final Logger log = LoggerFactory.getLogger(LocalAiService.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String defaultModel;

    public LocalAiService(
            RestTemplate restTemplate,
            @Value("${ai.local.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ai.local.model:mistral}") String defaultModel) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
    }

    public AiResult generateCompletion(AiGenerateRequest request) {
        String prompt = buildPrompt(request);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", resolveModel(request.getFormat(), null));
        payload.put("prompt", prompt);
        payload.put("stream", false);

        if (request.getFormat() != null && request.getFormat().equalsIgnoreCase("json")) {
            payload.put("format", "json");
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/api/generate")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<OllamaGenerateResponse> response = restTemplate.postForEntity(
                    url,
                    entity,
                    OllamaGenerateResponse.class
            );

            if (!response.hasBody() || response.getBody() == null) {
                throw new LocalAiServiceException("Empty response from local AI service");
            }

            OllamaGenerateResponse body = response.getBody();
            String content = sanitizeContent(body.getResponse(), request.getTask());

            if (content == null || content.isBlank()) {
                throw new LocalAiServiceException("Local AI service returned no content");
            }

            return new AiResult(content.trim(), body.getModel() != null ? body.getModel() : defaultModel);
        } catch (RestClientException e) {
            log.error("Local AI request failed: {}", e.getMessage(), e);
            throw new LocalAiServiceException("Local AI invocation failed: " + e.getMessage(), e);
        }
    }

    public AiResult chat(AiChatRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", resolveModel(null, request.getModel()));
        payload.put("stream", false);

        List<Map<String, String>> messages = new ArrayList<>();

        String systemPrompt = buildSystemPrompt(request.getTask(), request.getContext());
        if (!systemPrompt.isBlank()) {
            messages.add(Map.of(
                    "role", "system",
                    "content", systemPrompt
            ));
        }

        request.getMessages().forEach(message -> messages.add(Map.of(
                "role", sanitizeRole(message.getRole()),
                "content", message.getContent()
        )));

        payload.put("messages", messages);

        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/api/chat")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<OllamaChatResponse> response = restTemplate.postForEntity(
                    url,
                    entity,
                    OllamaChatResponse.class
            );

            if (!response.hasBody() || response.getBody() == null) {
                throw new LocalAiServiceException("Empty chat response from local AI service");
            }

            OllamaChatResponse body = response.getBody();

            if (body.getMessage() == null || body.getMessage().getContent() == null || body.getMessage().getContent().isBlank()) {
                throw new LocalAiServiceException("Local AI chat returned no content");
            }

            String content = sanitizeContent(body.getMessage().getContent(), request.getTask());
            return new AiResult(content, body.getModel() != null ? body.getModel() : defaultModel);
        } catch (RestClientException e) {
            log.error("Local AI chat request failed: {}", e.getMessage(), e);
            throw new LocalAiServiceException("Local AI chat failed: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(AiGenerateRequest request) {
        StringBuilder builder = new StringBuilder();

        switch (request.getTask()) {
            case "configmap_yaml" -> builder.append("""
                    You are an assistant that writes Kubernetes ConfigMap YAML.
                    Respond with YAML only. Avoid explanations.
                    """);
            case "diagram_nodes" -> builder.append("""
                    You are an assistant that proposes Kubernetes architecture blocks.
                    Return strictly valid JSON with a top-level "nodes" array.
                    Each node object must include: "type" (istio|service|deployment|container|configmap|secret|volume|job),
                    "name" (kebab-case), optional "description", and optional "links" array where each entry
                    contains "target" (name referencing another node) and "type" (e.g. "connectsTo").
                    Do not include any other text.
                    """);
            default -> builder.append("You are a helpful assistant.\n");
        }

        if (request.getContext() != null && !request.getContext().isBlank()) {
            builder.append("\nExisting context:\n").append(request.getContext().trim()).append("\n");
        }

        builder.append("\nUser instruction:\n").append(request.getPrompt().trim()).append("\n");

        return builder.toString();
    }

    private String buildSystemPrompt(String task, String context) {
        StringBuilder builder = new StringBuilder();
        if (task != null) {
            switch (task) {
                case "configmap_yaml" ->
                        builder.append("You help author Kubernetes ConfigMaps and respond concisely.\n");
                case "diagram_helper" ->
                        builder.append("You are a Kubernetes architecture assistant. Reply in concise Markdown.\n");
                default -> builder.append("You are a helpful assistant.\n");
            }
        } else {
            builder.append("You are a helpful assistant.\n");
        }

        if (context != null && !context.isBlank()) {
            builder.append("Context:\n").append(context.trim()).append("\n");
        }
        return builder.toString();
    }

    private String resolveModel(String format, String requestedModel) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return requestedModel;
        }
        return defaultModel;
    }

    private String sanitizeRole(String role) {
        if (role == null) {
            return "user";
        }
        return switch (role.toLowerCase()) {
            case "assistant", "system", "user" -> role.toLowerCase();
            default -> "user";
        };
    }

    private String sanitizeContent(String content, String task) {
        if (content == null) {
            return "";
        }
        String sanitized = content.trim();
        if ("configmap_yaml".equalsIgnoreCase(task)) {
            sanitized = stripCodeFences(sanitized);
        }
        return sanitized;
    }

    private String stripCodeFences(String content) {
        String result = stripFence(content, "```");
        result = stripFence(result, "'''");
        result = stripFence(result, "\"\"\"");
        return result.trim();
    }

    private String stripFence(String text, String fence) {
        String result = text;
        if (result.startsWith(fence)) {
            result = stripLeadingLanguageMarker(result.substring(fence.length()));
        }
        if (result.endsWith(fence)) {
            result = result.substring(0, result.length() - fence.length());
        }
        return result.trim();
    }

    private String stripLeadingLanguageMarker(String text) {
        String withoutLeadingBreaks = stripLeadingLineBreaks(text);
        int lineBreakIndex = findLineBreak(withoutLeadingBreaks);
        if (lineBreakIndex == -1) {
            String candidate = withoutLeadingBreaks.trim();
            return isLanguageTag(candidate) ? "" : withoutLeadingBreaks;
        }
        String firstLine = withoutLeadingBreaks.substring(0, lineBreakIndex).replace("\r", "").trim();
        if (isLanguageTag(firstLine)) {
            String remainder = withoutLeadingBreaks.substring(lineBreakIndex + 1);
            return stripLeadingLineBreaks(remainder);
        }
        return withoutLeadingBreaks;
    }

    private String stripLeadingLineBreaks(String text) {
        int index = 0;
        while (index < text.length()) {
            char ch = text.charAt(index);
            if (ch == '\n' || ch == '\r') {
                index++;
            } else {
                break;
            }
        }
        return text.substring(index);
    }

    private int findLineBreak(String text) {
        int newline = text.indexOf('\n');
        if (newline >= 0) {
            return newline;
        }
        return text.indexOf('\r');
    }

    private boolean isLanguageTag(String value) {
        if (value == null) {
            return false;
        }
        return switch (value.trim().toLowerCase()) {
            case "yaml", "yml", "json" -> true;
            default -> false;
        };
    }

    public static class AiResult {
        private final String content;
        private final String model;

        public AiResult(String content, String model) {
            this.content = content;
            this.model = model;
        }

        public String getContent() {
            return content;
        }

        public String getModel() {
            return model;
        }
    }

    public static class LocalAiServiceException extends RuntimeException {
        public LocalAiServiceException(String message) {
            super(message);
        }

        public LocalAiServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OllamaGenerateResponse {
        private String model;
        private String response;

        @JsonProperty("model")
        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getResponse() {
            return response;
        }

        public void setResponse(String response) {
            this.response = response;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OllamaChatResponse {
        private String model;
        private OllamaMessage message;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public OllamaMessage getMessage() {
            return message;
        }

        public void setMessage(OllamaMessage message) {
            this.message = message;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OllamaMessage {
        private String role;
        private String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
