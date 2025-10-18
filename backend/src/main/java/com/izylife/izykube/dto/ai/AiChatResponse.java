package com.izylife.izykube.dto.ai;

import java.util.List;

public class AiChatResponse {

    private final List<AiChatMessage> messages;
    private final String model;
    private final String task;

    public AiChatResponse(List<AiChatMessage> messages, String model, String task) {
        this.messages = messages;
        this.model = model;
        this.task = task;
    }

    public List<AiChatMessage> getMessages() {
        return messages;
    }

    public String getModel() {
        return model;
    }

    public String getTask() {
        return task;
    }
}
