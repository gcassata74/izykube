package com.izylife.izykube.dto.ai;

import jakarta.validation.constraints.NotBlank;

public class AiImportRequest {

    @NotBlank
    private String yaml;

    private String name;

    public String getYaml() {
        return yaml;
    }

    public void setYaml(String yaml) {
        this.yaml = yaml;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
