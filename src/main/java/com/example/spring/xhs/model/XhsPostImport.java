package com.example.spring.xhs.model;

import java.time.Instant;
import java.util.List;

public record XhsPostImport(
        XhsSourceType sourceType,
        String sourcePostId,
        String sourceUrl,
        String accessUrl,
        String authorKey,
        String title,
        String content,
        String noteType,
        List<String> tags,
        Instant publishedAt,
        Instant collectedAt,
        XhsMetrics metrics,
        String rawJson) {

    public XhsPostImport {
        sourceType = sourceType == null ? XhsSourceType.FILE_IMPORT : sourceType;
        sourcePostId = safe(sourcePostId);
        sourceUrl = safe(sourceUrl);
        accessUrl = safe(accessUrl);
        authorKey = safe(authorKey);
        title = safe(title);
        content = safe(content);
        noteType = safe(noteType);
        tags = tags == null ? List.of() : tags.stream().filter(java.util.Objects::nonNull).map(String::strip).filter(tag -> !tag.isBlank()).distinct().toList();
        collectedAt = collectedAt == null ? Instant.now() : collectedAt;
        metrics = metrics == null ? XhsMetrics.empty() : metrics;
        rawJson = safe(rawJson);
        if (sourcePostId.isBlank()) {
            throw new IllegalArgumentException("小红书笔记缺少 sourcePostId");
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
