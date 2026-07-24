package com.example.spring.wechat.knowledge.model;

import java.time.Instant;

/**
 * 知识库文档元信息，正文 chunk 存在 Qdrant payload 中。
 */
public record KnowledgeDocument(
        long id,
        String sessionKey,
        String title,
        String sourceType,
        String sourceUrl,
        String tags,
        String contentHash,
        int chunkCount,
        Instant createdAt,
        Instant updatedAt,
        boolean deleted) {
}
