package com.example.spring.wechat.context;

import java.time.Instant;

public record MemoryGraphNode(
        long id,
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
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        boolean deleted) {

    public MemoryGraphNode {
        sessionKey = clean(sessionKey);
        topicKey = clean(topicKey);
        title = clean(title);
        content = clean(content);
        summary = clean(summary);
        sourceType = clean(sourceType);
        sourceRef = clean(sourceRef);
        tags = clean(tags);
        nodeType = nodeType == null ? MemoryNodeType.CONVERSATION_SUMMARY : nodeType;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
