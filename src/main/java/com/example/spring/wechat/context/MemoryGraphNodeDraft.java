package com.example.spring.wechat.context;

import java.time.Instant;

public record MemoryGraphNodeDraft(
        String sessionKey,
        Long conversationId,
        MemoryNodeType nodeType,
        String topicKey,
        String title,
        String content,
        String summary,
        double importanceScore,
        double relevanceScore,
        double confidenceScore,
        Long sourceMessageStartId,
        Long sourceMessageEndId,
        String sourceType,
        String sourceRef,
        String tags,
        Instant expiresAt) {

    public MemoryGraphNodeDraft {
        sessionKey = sessionKey == null ? "" : sessionKey.strip();
        nodeType = nodeType == null ? MemoryNodeType.CONVERSATION_SUMMARY : nodeType;
        topicKey = topicKey == null ? "" : topicKey.strip();
        title = title == null || title.isBlank() ? "未命名记忆" : title.strip();
        content = content == null ? "" : content.strip();
        summary = summary == null ? "" : summary.strip();
        sourceType = sourceType == null ? "" : sourceType.strip();
        sourceRef = sourceRef == null ? "" : sourceRef.strip();
        tags = tags == null ? "" : tags.strip();
        importanceScore = clamp(importanceScore);
        relevanceScore = clamp(relevanceScore);
        confidenceScore = clamp(confidenceScore);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
