package com.example.spring.xhs.model;

import com.example.spring.xhs.source.XhsCollectionStatus;

import java.time.Instant;

public record XhsCollectionJob(
        String jobKey,
        long projectId,
        String projectKey,
        String projectName,
        XhsSourceType sourceType,
        String query,
        String externalJobId,
        XhsCollectionStatus status,
        int attemptCount,
        Instant startedAt) {
}
