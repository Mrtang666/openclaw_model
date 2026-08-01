package com.example.spring.xhs.model;

public record XhsMetrics(
        long likedCount,
        long collectedCount,
        long commentCount,
        long shareCount) {

    public XhsMetrics {
        likedCount = Math.max(0, likedCount);
        collectedCount = Math.max(0, collectedCount);
        commentCount = Math.max(0, commentCount);
        shareCount = Math.max(0, shareCount);
    }

    public static XhsMetrics empty() {
        return new XhsMetrics(0, 0, 0, 0);
    }
}
