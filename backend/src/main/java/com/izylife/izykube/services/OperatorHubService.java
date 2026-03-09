package com.izylife.izykube.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.izykube.dto.operatorhub.OperatorHubListResponseDTO;
import com.izylife.izykube.dto.operatorhub.OperatorHubOperatorDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperatorHubService {

    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final int PAGE_SIZE = 100;
    private static final Pattern CSV_VERSION_SUFFIX = Pattern.compile("\\.v\\d.*$");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${operatorhub.github.repo:k8s-operatorhub/community-operators}")
    private String githubRepo;

    @Value("${operatorhub.github.path:operators}")
    private String githubPath;

    @Value("${operatorhub.github.token:}")
    private String githubToken;

    @Value("${operatorhub.github.api-base-url}")
    private String githubApiBaseUrl;

    @Value("${operatorhub.api.url}")
    private String operatorHubApiUrl;

    @Value("${operatorhub.install.base-url}")
    private String installBaseUrl;

    @Value("${operatorhub.base-url}")
    private String operatorHubBaseUrl;

    private final AtomicReference<CacheEntry> cacheRef = new AtomicReference<>();

    public OperatorHubListResponseDTO listOperators(String query, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        String normalizedQuery = StringUtils.hasText(query) ? query.trim().toLowerCase(Locale.ROOT) : null;

        List<OperatorHubOperatorDTO> operators = getCachedOperators();
        List<OperatorHubOperatorDTO> filtered = operators;
        if (normalizedQuery != null) {
            filtered = operators.stream()
                    .filter(operator -> safeLower(operator.getName()).contains(normalizedQuery))
                    .toList();
        }

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);

        List<OperatorHubOperatorDTO> items = filtered.subList(fromIndex, toIndex).stream()
                .map(this::toDto)
                .toList();

        OperatorHubListResponseDTO response = new OperatorHubListResponseDTO();
        response.setItems(items);
        response.setPage(safePage);
        response.setSize(safeSize);
        response.setTotal(total);
        response.setQuery(normalizedQuery);
        return response;
    }

    public String getInstallYaml(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Operator name is required");
        }
        String trimmed = name.trim();
        try {
            return fetchInstallYamlByKey(trimmed);
        } catch (Exception e) {
            String fallback = normalizeInstallKey(trimmed);
            if (!trimmed.equals(fallback)) {
                return fetchInstallYamlByKey(fallback);
            }
            throw e;
        }
    }

    private OperatorHubOperatorDTO toDto(OperatorHubOperatorDTO source) {
        OperatorHubOperatorDTO dto = new OperatorHubOperatorDTO();
        dto.setName(source.getName());
        dto.setInstallKey(source.getInstallKey());
        dto.setIconUrl(source.getIconUrl());
        dto.setInstallYamlUrl(buildInstallYamlUrl(source.getInstallKey()));
        return dto;
    }

    private String buildInstallYamlUrl(String name) {
        if (installBaseUrl.endsWith("/")) {
            return installBaseUrl + name + ".yaml";
        }
        return installBaseUrl + "/" + name + ".yaml";
    }

    private List<OperatorHubOperatorDTO> getCachedOperators() {
        CacheEntry entry = cacheRef.get();
        if (entry != null && !entry.isExpired()) {
            return entry.operators;
        }

        synchronized (this) {
            CacheEntry refreshed = cacheRef.get();
            if (refreshed != null && !refreshed.isExpired()) {
                return refreshed.operators;
            }
            try {
                List<OperatorHubOperatorDTO> operators = fetchOperators();
                CacheEntry newEntry = new CacheEntry(operators, Instant.now().plus(CACHE_TTL));
                cacheRef.set(newEntry);
                return newEntry.operators;
            } catch (Exception e) {
                log.error("Failed to refresh OperatorHub operators list: {}", e.getMessage(), e);
                if (entry != null) {
                    return entry.operators;
                }
                throw e;
            }
        }
    }

    private List<OperatorHubOperatorDTO> fetchOperators() {
        try {
            List<OperatorHubOperatorDTO> fromOperatorHub = fetchFromOperatorHubApi();
            if (!fromOperatorHub.isEmpty()) {
                return normalizeOperators(fromOperatorHub);
            }
        } catch (Exception e) {
            log.warn("OperatorHub API fetch failed, fallback to GitHub: {}", e.getMessage());
        }

        try {
            return normalizeOperators(fetchFromGitHub());
        } catch (Exception e) {
            String message = e.getMessage() == null ? "unknown error" : e.getMessage();
            if (isGitHubRateLimit(message)) {
                throw new IllegalStateException(
                        "GitHub API rate limit exceeded. Configure `operatorhub.github.token` to increase limits.", e
                );
            }
            throw e;
        }
    }

    private List<OperatorHubOperatorDTO> normalizeOperators(List<OperatorHubOperatorDTO> operators) {
        Map<String, OperatorHubOperatorDTO> byName = operators.stream()
                .filter(operator -> StringUtils.hasText(operator.getName()))
                .collect(Collectors.toMap(
                        operator -> operator.getName().trim().toLowerCase(Locale.ROOT),
                        operator -> {
                            OperatorHubOperatorDTO normalized = new OperatorHubOperatorDTO();
                            normalized.setName(operator.getName().trim());
                            normalized.setInstallKey(normalizeInstallKey(
                                    StringUtils.hasText(operator.getInstallKey()) ? operator.getInstallKey() : operator.getName()
                            ));
                            normalized.setIconUrl(trimToNull(operator.getIconUrl()));
                            return normalized;
                        },
                        (left, right) -> {
                            if (!StringUtils.hasText(left.getIconUrl()) && StringUtils.hasText(right.getIconUrl())) {
                                left.setIconUrl(right.getIconUrl());
                            }
                            if (!StringUtils.hasText(left.getInstallKey()) && StringUtils.hasText(right.getInstallKey())) {
                                left.setInstallKey(right.getInstallKey());
                            }
                            return left;
                        }
                ));

        return byName.values().stream()
                .sorted(Comparator.comparing(operator -> operator.getName().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    private List<OperatorHubOperatorDTO> fetchFromGitHub() {
        List<OperatorHubOperatorDTO> operators = new ArrayList<>();
        int page = 1;
        while (true) {
            String url = String.format(
                    "%s/repos/%s/contents/%s?per_page=%d&page=%d",
                    trimTrailingSlash(githubApiBaseUrl),
                    githubRepo,
                    githubPath,
                    PAGE_SIZE,
                    page
            );
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Failed to fetch operator list from GitHub");
            }

            JsonNode root = readJson(response.getBody());
            if (!root.isArray() || root.size() == 0) {
                break;
            }

            for (JsonNode node : root) {
                if ("dir".equals(node.path("type").asText())) {
                    String name = node.path("name").asText(null);
                    if (StringUtils.hasText(name)) {
                        OperatorHubOperatorDTO dto = new OperatorHubOperatorDTO();
                        dto.setName(name.trim());
                        dto.setInstallKey(normalizeInstallKey(name.trim()));
                        operators.add(dto);
                    }
                }
            }

            if (root.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return operators;
    }

    private List<OperatorHubOperatorDTO> fetchFromOperatorHubApi() {
        ResponseEntity<String> response = restTemplate.exchange(
                operatorHubApiUrl,
                HttpMethod.GET,
                new HttpEntity<>(buildOperatorHubHeaders()),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Failed to fetch operator list from OperatorHub API");
        }

        JsonNode root = readJson(response.getBody());
        List<OperatorHubOperatorDTO> operators = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode node : root) {
                extractOperator(node).ifPresent(operators::add);
            }
        } else if (root.isObject()) {
            JsonNode items = root.path("items");
            if (items.isArray()) {
                for (JsonNode node : items) {
                    extractOperator(node).ifPresent(operators::add);
                }
            } else {
                root.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue();
                    if (value.isArray()) {
                        for (JsonNode node : value) {
                            extractOperator(node).ifPresent(operators::add);
                        }
                    }
                });
            }
        }

        return operators;
    }

    private java.util.Optional<OperatorHubOperatorDTO> extractOperator(JsonNode node) {
        if (node == null || node.isNull()) {
            return java.util.Optional.empty();
        }
        String[] nameCandidates = new String[] {
                node.path("name").asText(null),
                node.path("package_name").asText(null),
                node.path("operator_name").asText(null),
                node.path("metadata").path("name").asText(null),
                node.path("package").path("name").asText(null)
        };

        String name = null;
        for (String candidate : nameCandidates) {
            if (StringUtils.hasText(candidate)) {
                name = candidate.trim();
                break;
            }
        }
        if (!StringUtils.hasText(name)) {
            return java.util.Optional.empty();
        }

        OperatorHubOperatorDTO dto = new OperatorHubOperatorDTO();
        dto.setName(name);
        dto.setInstallKey(extractInstallKey(node, name));
        dto.setIconUrl(extractIconUrl(node));
        return java.util.Optional.of(dto);
    }

    private String extractInstallKey(JsonNode node, String defaultName) {
        String[] candidates = new String[] {
                node.path("package_name").asText(null),
                node.path("package").path("name").asText(null),
                node.path("metadata").path("name").asText(null),
                defaultName
        };
        for (String candidate : candidates) {
            String normalized = normalizeInstallKey(candidate);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return normalizeInstallKey(defaultName);
    }

    private String fetchInstallYamlByKey(String installKey) {
        String url = installBaseUrl.endsWith("/")
                ? installBaseUrl + installKey + ".yaml"
                : installBaseUrl + "/" + installKey + ".yaml";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Failed to fetch install YAML for " + installKey);
        }
        return response.getBody();
    }

    private String extractIconUrl(JsonNode node) {
        String[] directCandidates = new String[] {
                node.path("iconUrl").asText(null),
                node.path("icon_url").asText(null),
                node.path("logo").asText(null),
                node.path("logo_url").asText(null),
                node.path("defaultChannel").path("currentCSVDesc").path("icon").path(0).path("url").asText(null),
                node.path("default_channel").path("currentCSVDesc").path("icon").path(0).path("url").asText(null),
                node.path("default_channel").path("currentCSVDesc").path("icon").path(0).path("base64data").asText(null),
                node.path("package").path("icon").path("url").asText(null),
                node.path("package").path("icon").path("base64data").asText(null)
        };

        for (String candidate : directCandidates) {
            String normalized = normalizeIcon(candidate);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }

        JsonNode icon = node.path("icon");
        if (icon.isArray()) {
            for (JsonNode item : icon) {
                String normalized = normalizeIcon(item.path("url").asText(null));
                if (!StringUtils.hasText(normalized)) {
                    normalized = normalizeIcon(item.path("base64data").asText(null));
                }
                if (StringUtils.hasText(normalized)) {
                    return normalized;
                }
            }
        } else if (icon.isObject()) {
            String normalized = normalizeIcon(icon.path("url").asText(null));
            if (!StringUtils.hasText(normalized)) {
                normalized = normalizeIcon(icon.path("base64data").asText(null));
            }
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private String normalizeIcon(String value) {
        String trimmed = trimToNull(value);
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:image/")) {
            return trimmed;
        }
        if (trimmed.startsWith("/")) {
            return trimTrailingSlash(operatorHubBaseUrl) + trimmed;
        }
        if (trimmed.matches("^[A-Za-z0-9+/=]+$") && trimmed.length() > 128) {
            return "data:image/png;base64," + trimmed;
        }
        return null;
    }

    private boolean isGitHubRateLimit(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("rate limit") || normalized.contains("api rate limit exceeded");
    }

    private HttpHeaders buildOperatorHubHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "izykube-operatorhub-client");
        return headers;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "izykube-operatorhub-client");
        if (StringUtils.hasText(githubToken)) {
            headers.setBearerAuth(githubToken.trim());
        }
        return headers;
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse GitHub response", e);
        }
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeInstallKey(String value) {
        String trimmed = trimToNull(value);
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        return CSV_VERSION_SUFFIX.matcher(trimmed).replaceFirst("");
    }

    private String trimTrailingSlash(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return "";
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static final class CacheEntry {
        private final List<OperatorHubOperatorDTO> operators;
        private final Instant expiresAt;

        private CacheEntry(List<OperatorHubOperatorDTO> operators, Instant expiresAt) {
            this.operators = operators;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
