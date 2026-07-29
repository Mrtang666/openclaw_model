package com.example.spring.xhs.source;

public record XhsCollectionRequest(
        String projectKey,
        String projectName,
        String query,
        int limit,
        String cursor) {

    public XhsCollectionRequest {
        projectKey = required(projectKey, "projectKey");
        projectName = projectName == null || projectName.isBlank() ? projectKey : projectName.strip();
        query = required(query, "query");
        limit = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100));
        cursor = cursor == null ? "" : cursor.strip();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.strip();
    }
}
