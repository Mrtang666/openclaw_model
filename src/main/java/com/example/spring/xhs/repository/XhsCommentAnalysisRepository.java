package com.example.spring.xhs.repository;

import com.example.spring.xhs.analysis.XhsAnalysisLlmClient;
import com.example.spring.xhs.analysis.XhsCommentAnalysisCandidate;
import com.example.spring.xhs.analysis.XhsCommentAssessment;

import java.time.Instant;
import java.util.List;

public interface XhsCommentAnalysisRepository {

    List<XhsCommentAnalysisCandidate> claimPending(
            String version, int limit, int maxAttempts, int minimumRuleRiskScore,
            int minimumLikes, Instant staleBefore);

    void save(XhsCommentAnalysisCandidate candidate, String version,
              XhsCommentAssessment assessment, Instant analyzedAt);

    void fail(List<XhsCommentAnalysisCandidate> candidates, int maxAttempts,
              String message, Instant failedAt);

    void recordExecution(String batchKey, String version, int commentCount,
                         XhsAnalysisLlmClient.Response response, String status,
                         String errorMessage, Instant createdAt);
}
