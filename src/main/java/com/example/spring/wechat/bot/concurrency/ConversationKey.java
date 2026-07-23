package com.example.spring.wechat.bot.concurrency;

public record ConversationKey(String connectionId, String userId) {

    public ConversationKey {
        connectionId = normalize(connectionId, "unknown-connection");
        userId = normalize(userId, "unknown-user");
    }

    public String sessionKey() {
        return "clawbot:" + connectionId + ":" + userId;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
