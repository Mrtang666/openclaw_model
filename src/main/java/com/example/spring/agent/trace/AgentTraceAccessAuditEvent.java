package com.example.spring.agent.trace;

public record AgentTraceAccessAuditEvent(
        String actor,
        String action,
        String targetType,
        String targetKey,
        boolean allowed,
        String reason,
        String remoteAddress,
        String userAgent) {

    public AgentTraceAccessAuditEvent {
        actor = defaultIfBlank(actor, "anonymous");
        action = defaultIfBlank(action, "UNKNOWN");
        targetType = defaultIfBlank(targetType, "UNKNOWN");
        targetKey = defaultIfBlank(targetKey, "");
        reason = defaultIfBlank(reason, "UNKNOWN");
        remoteAddress = clean(remoteAddress);
        userAgent = clean(userAgent);
    }

    private static String defaultIfBlank(String value, String fallback) {
        String text = clean(value);
        return text.isBlank() ? fallback : text;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
