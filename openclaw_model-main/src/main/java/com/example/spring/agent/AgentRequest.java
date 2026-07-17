package com.example.spring.agent;

import com.example.spring.memory.MemoryMessage;
import java.util.List;

public record AgentRequest(
    String userId,
    Long messageId,
    String text,
    List<ImageAsset> images,
    int attachedImageCount,
    List<MemoryMessage> history,
    List<ImageAsset> referencedImages) {

    public AgentRequest {
        text = text == null ? "" : text.trim();
        images = images == null ? List.of() : List.copyOf(images);
        attachedImageCount = Math.max(attachedImageCount, images.size());
        history = history == null ? List.of() : List.copyOf(history);
        referencedImages = referencedImages == null ? List.of() : List.copyOf(referencedImages);
    }

    public AgentRequest(
        String userId,
        Long messageId,
        String text,
        List<ImageAsset> images,
        int attachedImageCount) {
        this(userId, messageId, text, images, attachedImageCount, List.of(), List.of());
    }

    public boolean hasImages() {
        return attachedImageCount > 0;
    }

    public boolean hasReferencedImages() {
        return !referencedImages.isEmpty();
    }

    public AgentRequest withText(String newText) {
        return new AgentRequest(
            userId,
            messageId,
            newText,
            images,
            attachedImageCount,
            history,
            referencedImages);
    }

    public AgentRequest withMemory(
        List<MemoryMessage> memoryHistory,
        List<ImageAsset> memoryImages) {
        return new AgentRequest(
            userId,
            messageId,
            text,
            images,
            attachedImageCount,
            memoryHistory,
            memoryImages);
    }
}
