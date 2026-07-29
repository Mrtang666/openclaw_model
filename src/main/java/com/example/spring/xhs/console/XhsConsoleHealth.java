package com.example.spring.xhs.console;

import java.time.Instant;

public record XhsConsoleHealth(
        String status,
        boolean databaseUp,
        boolean collectorEnabled,
        boolean collectorUp,
        String collectorMessage,
        int runningJobs,
        Instant checkedAt) {
}
