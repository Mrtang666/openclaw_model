package com.example.spring.xhs.analysis;

public enum XhsSentiment {
    POSITIVE,
    NEUTRAL,
    NEGATIVE;

    public static XhsSentiment from(String value) {
        if (value == null || value.isBlank()) {
            return NEUTRAL;
        }
        try {
            return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NEUTRAL;
        }
    }
}
