package com.izylife.izykube.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration holder for CORS settings so that both Spring Security and MVC use the same source.
 */
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://0.0.0.0:*",
            "http://[::1]:*",
            "ionic://localhost",
            "capacitor://localhost"
    ));

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        if (allowedOriginPatterns == null || allowedOriginPatterns.isEmpty()) {
            return;
        }
        this.allowedOriginPatterns = new ArrayList<>(allowedOriginPatterns);
    }
}
