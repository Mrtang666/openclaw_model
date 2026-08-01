package com.example.spring.xhs.analysis;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class XhsRiskScorer {

    private static final Set<String> OVERRIDE_CATEGORIES = Set.of("CONSUMER_SAFETY", "LEGAL", "REGULATORY");

    public XhsRiskAssessment score(XhsAnalysisCandidate candidate, XhsSemanticAssessment semantic) {
        return score(candidate, semantic, XhsTrendSignals.empty());
    }

    public XhsRiskAssessment score(
            XhsAnalysisCandidate candidate,
            XhsSemanticAssessment semantic,
            XhsTrendSignals trendSignals) {
        XhsTrendSignals signals = trendSignals == null ? XhsTrendSignals.empty() : trendSignals;
        int severity = semantic.severity() * 7;
        long engagement = candidate.metrics().likedCount()
                + candidate.metrics().collectedCount() * 2
                + candidate.metrics().commentCount() * 3
                + candidate.metrics().shareCount() * 4;
        int influence = Math.min(20, (int) Math.round(Math.log10(engagement + 1) * 5));
        int velocity = Math.min(25, (int) Math.round(Math.log10(signals.engagementGrowthPerHour() + 1) * 6));
        int credibility = 5;
        int recurrence = Math.min(10, signals.recurrenceCount() * 2);
        int total = severity + velocity + influence + credibility + recurrence;
        if (semantic.severity() >= 4 && OVERRIDE_CATEGORIES.contains(semantic.riskCategory())) {
            total = Math.max(total, 80);
        }
        total = Math.max(0, Math.min(100, total));
        Map<String, Integer> components = new LinkedHashMap<>();
        components.put("contentSeverity", severity);
        components.put("velocity", velocity);
        components.put("influence", influence);
        components.put("credibility", credibility);
        components.put("recurrence", recurrence);
        return new XhsRiskAssessment(total, level(total), components);
    }

    private String level(int score) {
        if (score >= 80) {
            return "CRITICAL";
        }
        if (score >= 60) {
            return "WARNING";
        }
        if (score >= 40) {
            return "WATCH";
        }
        return "NORMAL";
    }
}
