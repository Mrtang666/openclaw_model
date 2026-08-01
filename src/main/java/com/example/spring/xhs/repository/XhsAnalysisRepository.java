package com.example.spring.xhs.repository;

import com.example.spring.xhs.analysis.XhsAnalysisCandidate;
import com.example.spring.xhs.analysis.XhsAnalysisResult;
import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.analysis.XhsOpinionView;
import com.example.spring.xhs.analysis.XhsTrendSignals;

import java.time.Instant;
import java.util.List;

public interface XhsAnalysisRepository {

    List<XhsAnalysisCandidate> findUnanalyzed(String analysisVersion, int limit);

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
