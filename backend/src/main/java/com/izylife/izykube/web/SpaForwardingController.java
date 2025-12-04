package com.izylife.izykube.web;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class SpaForwardingController {

    private static final String SPA_PATH_REGEX =
            "^(?!.*\\.)(?!api|authenticate|swagger-ui|api-docs|v3|actuator|ws|assets|static).*$";

    private final Resource indexHtml;
    private final Resource fallbackHtml;

    public SpaForwardingController(ResourceLoader resourceLoader) {
        this.indexHtml = resourceLoader.getResource("classpath:/static/index.html");
        this.fallbackHtml = new ByteArrayResource("<!doctype html><app-root></app-root>"
                .getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = {"/", "/{path:" + SPA_PATH_REGEX + "}", "/{path:" + SPA_PATH_REGEX + "}/**"},
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> serveSpa(@PathVariable(value = "path", required = false) String ignored) {
        Resource resource = indexHtml.exists() && indexHtml.isReadable() ? indexHtml : fallbackHtml;
        return ResponseEntity
                .ok()
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
    }
}
