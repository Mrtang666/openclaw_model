package com.example.spring.agent;

import com.example.spring.document.DocumentAsset;
import com.example.spring.document.DocumentTaskPlan;
import com.example.spring.memory.MemoryMessage;
import java.util.List;

public record AgentRequest(
    String userId,
    Long messageId,
    String text,
    List<ImageAsset> images,
    int attachedImageCount,
    List<MemoryMessage> history,
    List<ImageAsset> referencedImages,
    List<DocumentAsset> documents,
    int attachedDocumentCount,
    List<DocumentAsset> referencedDocuments,
    DocumentTaskPlan documentTaskPlan) {

    public AgentRequest {
        text = text == null ? "" : text.trim();
        images = images == null ? List.of() : List.copyOf(images);
        attachedImageCount = Math.max(attachedImageCount, images.size());
        history = history == null ? List.of() : List.copyOf(history);
        referencedImages = referencedImages == null ? List.of() : List.copyOf(referencedImages);
        documents = documents == null ? List.of() : List.copyOf(documents);
        attachedDocumentCount = Math.max(attachedDocumentCount, documents.size());
        referencedDocuments = referencedDocuments == null
            ? List.of() : List.copyOf(referencedDocuments);
    }

    public AgentRequest(
        String userId,
        Long messageId,
        String text,
        List<ImageAsset> images,
        int attachedImageCount) {
        this(userId, messageId, text, images, attachedImageCount,
            List.of(), List.of(), List.of(), 0, List.of(), null);
    }

    public AgentRequest(
        String userId,
        Long messageId,
        String text,
        List<ImageAsset> images,
        int attachedImageCount,
        List<MemoryMessage> history,
        List<ImageAsset> referencedImages) {
        this(userId, messageId, text, images, attachedImageCount,
            history, referencedImages, List.of(), 0, List.of(), null);
    }

    public AgentRequest(
        String userId,
        Long messageId,
        String text,
        List<ImageAsset> images,
        int attachedImageCount,
        List<MemoryMessage> history,
        List<ImageAsset> referencedImages,
        List<DocumentAsset> documents,
        int attachedDocumentCount,
        List<DocumentAsset> referencedDocuments) {
        this(userId, messageId, text, images, attachedImageCount,
            history, referencedImages, documents, attachedDocumentCount,
            referencedDocuments, null);
    }

    public boolean hasImages() {
        return attachedImageCount > 0;
    }

    public boolean hasReferencedImages() {
        return !referencedImages.isEmpty();
    }

    public boolean hasDocuments() {
        return attachedDocumentCount > 0 || !documents.isEmpty();
    }

    public boolean hasReferencedDocuments() {
        return !referencedDocuments.isEmpty();
    }

    public AgentRequest withText(String newText) {
        return new AgentRequest(
            userId,
            messageId,
            newText,
            images,
            attachedImageCount,
            history,
            referencedImages,
            documents,
            attachedDocumentCount,
            referencedDocuments,
            documentTaskPlan);
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
            memoryImages,
            documents,
            attachedDocumentCount,
            referencedDocuments,
            documentTaskPlan);
    }

    public AgentRequest withDocuments(
        List<DocumentAsset> attached,
        int attachedCount,
        List<DocumentAsset> referenced) {
        return new AgentRequest(
            userId,
            messageId,
            text,
            images,
            attachedImageCount,
            history,
            referencedImages,
            attached,
            attachedCount,
            referenced,
            documentTaskPlan);
    }

    public AgentRequest withDocumentTaskPlan(DocumentTaskPlan plan) {
        return new AgentRequest(
            userId,
            messageId,
            text,
            images,
            attachedImageCount,
            history,
            referencedImages,
            documents,
            attachedDocumentCount,
            referencedDocuments,
            plan);
    }
}
