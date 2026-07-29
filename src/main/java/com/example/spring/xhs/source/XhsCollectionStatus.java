package com.example.spring.xhs.source;

public enum XhsCollectionStatus {
    PENDING,
    SUBMITTED,
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    FAILED;

    public boolean terminal() {
        return this == SUCCEEDED || this == PARTIAL || this == FAILED;
    }

    public static XhsCollectionStatus from(String value) {
        if (value == null || value.isBlank()) {
            return FAILED;
        }
        try {
            return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return FAILED;
        }
    }
}
