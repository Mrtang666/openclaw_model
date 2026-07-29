package com.example.spring.xhs.analysis;

import java.util.Map;

public record XhsRiskAssessment(
        int riskScore,
        String riskLevel,
        Map<String, Integer> components) {
}
