package com.example.spring.agent.trace;

import java.time.Instant;

public record AgentTraceAccessAuditView(
        long id,
        String actor,
        String action,
        String targetType,
        String targetKey,
        boolean allowed,
        String reason,
        String remoteAddress,
        String userAgent,
        Instant createdAt) {

    public AgentTraceAccessAuditView {
        actor = clean(actor);
        action = clean(action);
        targetType = clean(targetType);
        targetKey = clean(targetKey);
        reason = clean(reason);
        remoteAddress = clean(remoteAddress);
        userAgent = clean(userAgent);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
