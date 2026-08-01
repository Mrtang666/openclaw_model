package com.example.spring.xhs.model;

public record XhsImportResult(
        String projectKey,
        XhsSourceType sourceType,
        int postCount,
        int commentCount,
        int skippedCount) {
}
