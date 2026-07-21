package com.example.spring.agent;

import com.example.spring.document.GeneratedDocument;
import java.util.List;

public record AgentResponse(
    String text,
    List<ImageAsset> images,
    List<GeneratedDocument> files) {
    public AgentResponse {
        text = text == null ? "" : text.trim();
        images = images == null ? List.of() : List.copyOf(images);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public AgentResponse(String text, List<ImageAsset> images) {
        this(text, images, List.of());
    }

    public static AgentResponse text(String text) {
        return new AgentResponse(text, List.of(), List.of());
    }

    public static AgentResponse image(String text, ImageAsset image) {
        return new AgentResponse(text, List.of(image), List.of());
    }

    public static AgentResponse file(String text, GeneratedDocument file) {
        return new AgentResponse(text, List.of(), List.of(file));
    }
}
