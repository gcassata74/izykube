package com.izylife.izykube.dto.ai;

import jakarta.validation.constraints.NotBlank;

public class AiGenerateRequest {

    @NotBlank
    private String task;

    @NotBlank
    private String prompt;

    private String context;
    private String format;

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
