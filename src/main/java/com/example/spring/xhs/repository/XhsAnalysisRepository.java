package com.example.spring.xhs.repository;

import com.example.spring.xhs.analysis.XhsAnalysisCandidate;
import com.example.spring.xhs.analysis.XhsAnalysisResult;
import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.analysis.XhsOpinionView;
import com.example.spring.xhs.analysis.XhsTrendSignals;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface XhsAnalysisRepository {

    List<XhsAnalysisCandidate> findUnanalyzed(String analysisVersion, int limit);

    default List<XhsAnalysisClaim> claimUnanalyzed(String analysisVersion, int limit) {
        return findUnanalyzed(analysisVersion, limit).stream()
                .map(candidate -> new XhsAnalysisClaim(candidate, "legacy-" + UUID.randomUUID()))
                .toList();
    }

    default void releaseClaim(XhsAnalysisClaim claim) {
    }

    void saveAnalysis(XhsAnalysisResult result);

    XhsTrendSignals loadTrendSignals(long postId, long projectId, String riskCategory, Instant since);

    long upsertIncident(
            long projectId,
            String incidentKey,
            String category,
            String title,
            int riskScore,
            String riskLevel,
            Instant observedAt);

    void linkIncidentPost(long incidentId, long postId, Instant linkedAt);

    List<XhsOpinionView> searchOpinions(
            String projectKey,
            String keyword,
            String sentiment,
            int minimumRiskScore,
            int limit);

    List<XhsIncidentView> listIncidents(String projectKey, String status, int limit);
}
