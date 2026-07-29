package com.example.spring.xhs.source;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record XhsCollectorJobResult(
        XhsCollectionStatus status,
        boolean complete,
        String nextCursor,
        JsonNode records,
        String errorCode,
        String errorMessage,
        Instant collectedAt) {

    public XhsCollectorJobResult {
        status = status == null ? XhsCollectionStatus.FAILED : status;
        nextCursor = safe(nextCursor);
        errorCode = safe(errorCode);
        errorMessage = safe(errorMessage);
        collectedAt = collectedAt == null ? Instant.now() : collectedAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
