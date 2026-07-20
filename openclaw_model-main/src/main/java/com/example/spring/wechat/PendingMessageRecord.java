package com.example.spring.wechat;

public record PendingMessageRecord(
    long id,
    String userId,
    long messageId,
    String sourceType,
    String originalText,
    String normalizedText,
    MessageReplyStatus status,
    int replyAttempts,
    String lastError,
    long receivedAt,
    long updatedAt) {

    public String replayText() {
        if (normalizedText != null && !normalizedText.isBlank()) {
            return normalizedText;
        }
        return originalText == null ? "" : originalText;
    }
}
