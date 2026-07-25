package com.example.spring.wechat.knowledge.model;

/**
 * 知识库检索返回的相关片段。
 */
public record KnowledgeSearchResult(
        long documentId,
        String title,
        int chunkIndex,
        String content,
        String sourceType,
        String sourceUrl,
        double score) {
}
