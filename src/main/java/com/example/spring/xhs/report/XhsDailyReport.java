package com.example.spring.xhs.report;

import com.example.spring.xhs.analysis.XhsIncidentView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record XhsDailyReport(
        String projectKey,
        String projectName,
        LocalDate reportDate,
        Instant periodStart,
        Instant periodEnd,
        int collectedPosts,
        int analyzedPosts,
        int negativePosts,
        int negativeCommentPosts,
        int negativeImagePosts,
        int highRiskPosts,
        int newIncidents,
        int activeIncidents,
        int resolvedIncidents,
        int averageRiskScore,
        List<XhsRiskCategorySummary> categories,
        List<XhsIncidentView> topActiveIncidents,
        List<XhsReportPostSummary> topRiskPosts) {

    public XhsDailyReport {
        categories = categories == null ? List.of() : List.copyOf(categories);
        topActiveIncidents = topActiveIncidents == null ? List.of() : List.copyOf(topActiveIncidents);
        topRiskPosts = topRiskPosts == null ? List.of() : List.copyOf(topRiskPosts);
    }

    public XhsDailyReport(
            String projectKey, String projectName, LocalDate reportDate, Instant periodStart, Instant periodEnd,
            int collectedPosts, int analyzedPosts, int negativePosts, int highRiskPosts,
            int newIncidents, int activeIncidents, int resolvedIncidents, int averageRiskScore,
            List<XhsRiskCategorySummary> categories, List<XhsIncidentView> topActiveIncidents,
            List<XhsReportPostSummary> topRiskPosts) {
        this(projectKey, projectName, reportDate, periodStart, periodEnd, collectedPosts, analyzedPosts,
                negativePosts, 0, 0, highRiskPosts, newIncidents, activeIncidents, resolvedIncidents,
                averageRiskScore, categories, topActiveIncidents, topRiskPosts);
    }
}
