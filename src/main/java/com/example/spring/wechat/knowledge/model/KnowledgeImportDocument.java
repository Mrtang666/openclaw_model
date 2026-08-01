package com.example.spring.wechat.knowledge.model;

public record KnowledgeImportDocument(
        String title,
        String content,
        String sourceType,
        String sourceUrl,
        String tags) {
}
