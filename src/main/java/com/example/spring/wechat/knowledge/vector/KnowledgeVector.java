package com.example.spring.wechat.knowledge.vector;

import java.util.List;

/**
 * 写入向量库的知识片段。
 */
public record KnowledgeVector(
        String pointId,
        List<Float> vector,
        String sessionKey,
        long documentId,
        String title,
        int chunkIndex,
        String content,
        String sourceType,
        String sourceUrl,
        List<String> tags) {
}
