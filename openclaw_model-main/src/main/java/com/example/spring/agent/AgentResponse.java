package com.example.spring.agent;

import java.util.List;

public record AgentResponse(String text, List<ImageAsset> images) {
    public AgentResponse {
        text = text == null ? "" : text.trim();
        images = images == null ? List.of() : List.copyOf(images);
    }

    public static AgentResponse text(String text) {
        return new AgentResponse(text, List.of());
    }

    public static AgentResponse image(String text, ImageAsset image) {
        return new AgentResponse(text, List.of(image));
    }
}
