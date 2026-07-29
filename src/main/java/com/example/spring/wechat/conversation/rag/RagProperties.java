package com.example.spring.wechat.conversation.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        boolean enabled,
        boolean autoRetrieve,
        int topK,
        double minScore,
        int maxContextChars,
        boolean includeSources) {

    public RagProperties {
        topK = topK <= 0 ? 5 : topK;
        minScore = minScore <= 0 ? 0.2 : minScore;
        maxContextChars = maxContextChars <= 0 ? 6000 : maxContextChars;
    }
}
