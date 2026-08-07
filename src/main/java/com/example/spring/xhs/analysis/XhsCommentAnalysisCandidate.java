package com.example.spring.xhs.analysis;

public record XhsCommentAnalysisCandidate(
        long analysisId,
        long postId,
        String sourceCommentId,
        String content,
        long likedCount,
        int ruleRiskScore,
        String claimToken) {
}
