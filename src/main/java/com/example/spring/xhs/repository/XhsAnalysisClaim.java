package com.example.spring.xhs.repository;

import com.example.spring.xhs.analysis.XhsAnalysisCandidate;

public record XhsAnalysisClaim(XhsAnalysisCandidate candidate, String token) {
}
