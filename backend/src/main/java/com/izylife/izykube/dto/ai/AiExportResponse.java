package com.izylife.izykube.dto.ai;

public class AiExportResponse {

    private final String yaml;

    public AiExportResponse(String yaml) {
        this.yaml = yaml;
    }

    public String getYaml() {
        return yaml;
    }
}
