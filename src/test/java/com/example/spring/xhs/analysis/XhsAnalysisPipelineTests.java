package com.example.spring.xhs.analysis;

import com.example.spring.xhs.config.XhsAnalysisProperties;
import com.example.spring.xhs.model.XhsMetrics;
import com.example.spring.xhs.repository.XhsAnalysisRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XhsAnalysisPipelineTests {

    @Test
    void savesReviewResultAndCreatesHighRiskIncident() {
        FakeRepository repository = new FakeRepository();
        repository.candidates.add(new XhsAnalysisCandidate(
                11, 7, "brand-a", "过敏反馈", "使用后红肿", "url",
                Instant.now(), Instant.now(), new XhsMetrics(10, 2, 3, 1)));
        XhsSemanticAnalyzer analyzer = ignored -> new XhsSemanticAssessment(
                XhsSentiment.NEGATIVE, -0.8, List.of("产品安全"), "CONSUMER_SAFETY",
                5, 0.55, "用户反馈过敏", List.of("使用后红肿"));
        XhsAnalysisPipeline pipeline = new XhsAnalysisPipeline(
                repository, analyzer, new XhsRiskScorer(),
                new XhsAnalysisProperties(true, "test-v1", 20, 60, 0.65));

        int processed = pipeline.processPending();

        assertThat(processed).isEqualTo(1);
        assertThat(repository.saved.reviewStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(repository.saved.risk().riskScore()).isEqualTo(80);
        assertThat(repository.incidentPostId).isEqualTo(11);
        assertThat(repository.incidentKey).hasSize(64);
    }

    private static final class FakeRepository implements XhsAnalysisRepository {
        private final List<XhsAnalysisCandidate> candidates = new ArrayList<>();
        private XhsAnalysisResult saved;
        private String incidentKey;
        private long incidentPostId;

        @Override
        public List<XhsAnalysisCandidate> findUnanalyzed(String analysisVersion, int limit) {
            return candidates;
        }

        @Override
        public void saveAnalysis(XhsAnalysisResult result) {
            saved = result;
        }

        @Override
        public XhsTrendSignals loadTrendSignals(long postId, long projectId, String riskCategory, Instant since) {
            return XhsTrendSignals.empty();
        }

        @Override
        public long upsertIncident(long projectId, String incidentKey, String category, String title,
                                   int riskScore, String riskLevel, Instant observedAt) {
            this.incidentKey = incidentKey;
            return 99;
        }

        @Override
        public void linkIncidentPost(long incidentId, long postId, Instant linkedAt) {
            incidentPostId = postId;
        }

        @Override
        public List<XhsOpinionView> searchOpinions(String projectKey, String keyword, String sentiment, int minimumRiskScore, int limit) {
            return List.of();
        }

        @Override
        public List<XhsIncidentView> listIncidents(String projectKey, String status, int limit) {
            return List.of();
        }
    }
}
