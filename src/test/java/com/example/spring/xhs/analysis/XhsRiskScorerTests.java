package com.example.spring.xhs.analysis;

import com.example.spring.xhs.model.XhsMetrics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XhsRiskScorerTests {

    @Test
    void safetyRiskOverridesOrdinaryEngagementScore() {
        XhsAnalysisCandidate candidate = new XhsAnalysisCandidate(
                1, 2, "brand-a", "过敏反馈", "使用后红肿", "url",
                Instant.now(), Instant.now(), new XhsMetrics(2, 0, 1, 0));
        XhsSemanticAssessment semantic = new XhsSemanticAssessment(
                XhsSentiment.NEGATIVE, -0.9, List.of("产品安全"),
                "CONSUMER_SAFETY", 5, 0.9, "出现过敏反馈", List.of("使用后红肿"));

        XhsRiskAssessment risk = new XhsRiskScorer().score(candidate, semantic);

        assertThat(risk.riskScore()).isEqualTo(80);
        assertThat(risk.riskLevel()).isEqualTo("CRITICAL");
        assertThat(risk.components()).containsEntry("contentSeverity", 35);
    }

    @Test
    void includesVelocityAndRecurrenceSignals() {
        XhsAnalysisCandidate candidate = new XhsAnalysisCandidate(
                1, 2, "brand-a", "投诉", "退款迟迟未处理", "url",
                Instant.now(), Instant.now(), new XhsMetrics(100, 20, 30, 10));
        XhsSemanticAssessment semantic = new XhsSemanticAssessment(
                XhsSentiment.NEGATIVE, -0.8, List.of("售后"),
                "CONSUMER_COMPLAINT", 3, 0.9, "退款投诉", List.of("退款迟迟未处理"));

        XhsRiskAssessment risk = new XhsRiskScorer().score(
                candidate, semantic, new XhsTrendSignals(999, 6));

        assertThat(risk.components().get("velocity")).isGreaterThan(0);
        assertThat(risk.components()).containsEntry("recurrence", 10);
        assertThat(risk.riskScore()).isGreaterThan(40);
    }
}
