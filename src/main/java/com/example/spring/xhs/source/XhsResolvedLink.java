package com.example.spring.xhs.source;

public record XhsResolvedLink(
        boolean found,
        String accessUrl,
        String errorCode,
        String errorMessage) {

    public XhsResolvedLink {
        accessUrl = safe(accessUrl);
        errorCode = safe(errorCode);
        errorMessage = safe(errorMessage);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
