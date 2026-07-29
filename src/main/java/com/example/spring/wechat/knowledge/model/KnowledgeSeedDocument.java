package com.example.spring.wechat.knowledge.model;

public record KnowledgeSeedDocument(
        String title,
        String content,
        String sourceType,
        String sourceUrl,
        String tags) {
}
