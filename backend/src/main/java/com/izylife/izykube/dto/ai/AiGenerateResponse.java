package com.izylife.izykube.dto.ai;

public class AiGenerateResponse {

    private final String content;
    private final String task;
    private final String format;
    private final String model;

    public AiGenerateResponse(String content, String task, String format, String model) {
        this.content = content;
        this.task = task;
        this.format = format;
        this.model = model;
    }

    public String getContent() {
        return content;
    }

    public String getTask() {
        return task;
    }

    public String getFormat() {
        return format;
    }

    public String getModel() {
        return model;
    }
}
