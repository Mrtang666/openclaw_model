package com.example.spring.document;

public record PendingDocumentDelivery(
    long id,
    String userId,
    Long messageId,
    GeneratedDocument document) {
}
