package com.example.spring.xhs.incident;

import java.time.Instant;

public record XhsIncidentTransition(
        long incidentId,
        String projectKey,
        XhsIncidentStatus fromStatus,
        XhsIncidentStatus toStatus,
        boolean changed,
        Instant changedAt) {
}
