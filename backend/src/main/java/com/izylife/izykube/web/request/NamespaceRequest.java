package com.izylife.izykube.web.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NamespaceRequest {
    @NotBlank
    private String name;
    private String description;
}
