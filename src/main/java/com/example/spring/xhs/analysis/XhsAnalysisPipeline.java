package com.example.spring.xhs.analysis;

import com.example.spring.xhs.config.XhsAnalysisProperties;
import com.example.spring.xhs.repository.XhsAnalysisRepository;
import com.example.spring.xhs.alert.XhsAlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class XhsAnalysisPipeline {

    private final XhsAnalysisRepository repository;
    private final XhsSemanticAnalyzer semanticAnalyzer;
    private final XhsRiskScorer riskScorer;
    private final XhsAnalysisProperties properties;
    private final XhsAlertService alertService;

    @Autowired
    public XhsAnalysisPipeline(
            XhsAnalysisRepository repository,
            XhsSemanticAnalyzer semanticAnalyzer,
            XhsRiskScorer riskScorer,
            XhsAnalysisProperties properties,
            XhsAlertService alertService) {
        this.repository = repository;
        this.semanticAnalyzer = semanticAnalyzer;
        this.riskScorer = riskScorer;
        this.properties = properties;
        this.alertService = alertService;
    }

    XhsAnalysisPipeline(
            XhsAnalysisRepository repository,
            XhsSemanticAnalyzer semanticAnalyzer,
            XhsRiskScorer riskScorer,
            XhsAnalysisProperties properties) {
        this(repository, semanticAnalyzer, riskScorer, properties, null);
    }

    public int processPending() {
        if (!properties.enabled()) {
            return 0;
        }
        List<XhsAnalysisCandidate> candidates = repository.findUnanalyzed(properties.version(), properties.batchSize());
        candidates.forEach(this::analyze);
        return candidates.size();
    }

    private void analyze(XhsAnalysisCandidate candidate) {
        XhsSemanticAssessment semantic = semanticAnalyzer.analyze(candidate);
        XhsTrendSignals trendSignals = repository.loadTrendSignals(
                candidate.postId(), candidate.projectId(), semantic.riskCategory(), Instant.now().minus(java.time.Duration.ofDays(30)));
        XhsRiskAssessment risk = riskScorer.score(candidate, semantic, trendSignals);
        Instant now = Instant.now();
        XhsAnalysisResult result = new XhsAnalysisResult(
                candidate.postId(), properties.version(), semantic, risk,
                semantic.confidence() < properties.reviewConfidenceThreshold() ? "REVIEW_REQUIRED" : "AUTO_ACCEPTED",
                now);
        if (risk.riskScore() >= properties.minimumIncidentRiskScore()) {
            long incidentId = repository.upsertIncident(
                    candidate.projectId(), incidentKey(candidate, semantic), semantic.riskCategory(),
                    incidentTitle(semantic, candidate), risk.riskScore(), risk.riskLevel(),
                    candidate.publishedAt() == null ? candidate.collectedAt() : candidate.publishedAt());
            repository.linkIncidentPost(incidentId, candidate.postId(), now);
            if (alertService != null) {
                alertService.evaluate(incidentId, risk.riskScore(), risk.riskLevel());
            }
        }
        // Saving the analysis is the completion marker. Keep it last so an incident failure
        // leaves the post eligible for an idempotent retry.
        repository.saveAnalysis(result);
    }

    private String incidentKey(XhsAnalysisCandidate candidate, XhsSemanticAssessment semantic) {
        String aspect = semantic.aspects().isEmpty() ? semantic.riskCategory() : semantic.aspects().get(0);
        String raw = candidate.projectId() + "|" + semantic.riskCategory() + "|" + aspect.toLowerCase(java.util.Locale.ROOT);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成舆情事件键", exception);
        }
    }

    private String incidentTitle(XhsSemanticAssessment semantic, XhsAnalysisCandidate candidate) {
        String value = semantic.summary().isBlank() ? candidate.title() : semantic.summary();
        String text = value == null || value.isBlank() ? semantic.riskCategory() : value.strip();
        return text.length() <= 200 ? text : text.substring(0, 200);
    }
}
