package com.example.spring.xhs.source;

public record XhsCollectionRequest(
        String projectKey,
        String projectName,
        String query,
        int limit,
        String cursor,
        String sortMode,
        String timeRange,
        String noteType,
        int commentLimit) {

    public XhsCollectionRequest(
            String projectKey, String projectName, String query, int limit, String cursor) {
        this(projectKey, projectName, query, limit, cursor, "GENERAL", "ANY", "ALL", 100);
    }

    public XhsCollectionRequest(
            String projectKey, String projectName, String query, int limit, String cursor,
            String sortMode, String timeRange, String noteType) {
        this(projectKey, projectName, query, limit, cursor, sortMode, timeRange, noteType, 100);
    }

    public XhsCollectionRequest {
        projectKey = required(projectKey, "projectKey");
        projectName = projectName == null || projectName.isBlank() ? projectKey : projectName.strip();
        query = required(query, "query");
        limit = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100));
        cursor = cursor == null ? "" : cursor.strip();
        sortMode = choice(sortMode, "GENERAL", "GENERAL", "LATEST", "LIKES", "COMMENTS", "COLLECTS");
        timeRange = choice(timeRange, "ANY", "ANY", "DAY", "WEEK", "HALF_YEAR");
        noteType = choice(noteType, "ALL", "ALL", "VIDEO", "IMAGE");
        commentLimit = Math.max(0, Math.min(commentLimit, 1000));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.strip();
    }

    private static String choice(String value, String fallback, String... allowed) {
        String normalized = value == null || value.isBlank()
                ? fallback : value.strip().toUpperCase(java.util.Locale.ROOT);
        for (String item : allowed) {
            if (item.equals(normalized)) {
                return normalized;
            }
        }
        throw new IllegalArgumentException("unsupported collection option: " + normalized);
    }
}
