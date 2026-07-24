package com.example.spring.wechat.knowledge.model;

/**
 * 知识入库结果。
 */
public record KnowledgeIngestionResult(
        long documentId,
        String title,
        int chunkCount,
        boolean alreadyExists) {
}
