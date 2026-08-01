package com.example.spring.xhs.model;

import java.time.Instant;

public record XhsCommentImport(
        String sourcePostId,
        String sourceCommentId,
        String parentCommentId,
        String authorKey,
        String content,
        long likedCount,
        Instant publishedAt,
        Instant collectedAt,
        String rawJson) {

    public XhsCommentImport {
        sourcePostId = safe(sourcePostId);
        sourceCommentId = safe(sourceCommentId);
        parentCommentId = safe(parentCommentId);
        authorKey = safe(authorKey);
        content = safe(content);
        likedCount = Math.max(0, likedCount);
        collectedAt = collectedAt == null ? Instant.now() : collectedAt;
        rawJson = safe(rawJson);
        if (sourcePostId.isBlank() || sourceCommentId.isBlank()) {
            throw new IllegalArgumentException("小红书评论缺少笔记 ID 或评论 ID");
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
