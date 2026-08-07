package com.example.spring.xhs.repository;

import com.example.spring.xhs.analysis.XhsImageAnalysisCandidate;
import com.example.spring.xhs.analysis.XhsImageAssessment;
import com.example.spring.wechat.image.client.ImageUnderstandingClient;

import java.time.Instant;
import java.util.List;

public interface XhsImageAnalysisRepository {

    List<XhsImageAnalysisCandidate> claimPending(
            String version, int limit, int maxAttempts, Instant staleBefore);

    boolean reuseCached(XhsImageAnalysisCandidate candidate, String version, Instant analyzedAt);

    void save(XhsImageAnalysisCandidate candidate, String version,
              XhsImageAssessment assessment, Instant analyzedAt);

    void fail(XhsImageAnalysisCandidate candidate, int maxAttempts, String message, Instant failedAt);

    default void recordExecution(
            XhsImageAnalysisCandidate candidate, String version,
            ImageUnderstandingClient.Response response, String status,
            String errorMessage, long durationMs, Instant createdAt) {
    }
}
