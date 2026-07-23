package com.example.spring.wechat.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库切分和检索配置。
 */
@ConfigurationProperties(prefix = "knowledge")
public record KnowledgeProperties(
        int chunkSize,
        int chunkOverlap,
        int topK,
        int maxContextChars,
        double minScore) {

    public KnowledgeProperties {
        chunkSize = chunkSize <= 0 ? 800 : chunkSize;
        chunkOverlap = Math.max(0, Math.min(chunkOverlap, Math.max(0, chunkSize / 2)));
        topK = topK <= 0 ? 5 : topK;
        maxContextChars = maxContextChars <= 0 ? 6000 : maxContextChars;
        minScore = minScore <= 0 ? 0.2 : minScore;
    }
}
