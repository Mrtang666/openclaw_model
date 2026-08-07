package com.example.spring.xhs.analysis;

public record XhsImageAnalysisCandidate(
        long imageId,
        long postId,
        String imageHash,
        String imageUrl,
        String title,
        String content,
        String claimToken) {
}
