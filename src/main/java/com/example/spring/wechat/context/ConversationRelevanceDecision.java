package com.example.spring.wechat.context;

import java.util.List;

public record ConversationRelevanceDecision(
        RelevanceLevel relevance,
        double confidence,
        String currentTopic,
        String reason,
        List<String> relatedTopics) {

    public ConversationRelevanceDecision {
        relevance = relevance == null ? RelevanceLevel.WEAK : relevance;
        confidence = Math.max(0, Math.min(1, confidence));
        currentTopic = clean(currentTopic);
        reason = clean(reason);
        relatedTopics = relatedTopics == null
                ? List.of()
                : relatedTopics.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
    }

    public static ConversationRelevanceDecision weak(String reason) {
        return new ConversationRelevanceDecision(RelevanceLevel.WEAK, 0, "", reason, List.of());
    }

    public static ConversationRelevanceDecision strong(String topic, String reason) {
        return new ConversationRelevanceDecision(RelevanceLevel.STRONG, 1, topic, reason, List.of(topic));
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
