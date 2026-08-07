package com.example.spring.xhs.repository;

import com.example.spring.xhs.analysis.XhsAnalysisLlmClient;
import com.example.spring.xhs.analysis.XhsCommentAnalysisCandidate;
import com.example.spring.xhs.analysis.XhsCommentAssessment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MySqlXhsCommentAnalysisRepository implements XhsCommentAnalysisRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MySqlXhsCommentAnalysisRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<XhsCommentAnalysisCandidate> claimPending(
            String version, int limit, int maxAttempts, int minimumRuleRiskScore,
            int minimumLikes, Instant staleBefore) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE xhs_comment_analysis_results
                SET analysis_lock_token = ?, analysis_locked_at = ?
                WHERE id IN (
                    SELECT id FROM (
                        SELECT a.id
                        FROM xhs_comment_analysis_results a
                        JOIN xhs_comments c
                          ON c.post_id = a.post_id
                         AND c.source_comment_id = a.source_comment_id
                        WHERE a.is_negative = TRUE
                          AND a.attempt_count < ?
                          AND (a.analysis_status = 'PENDING'
                               OR a.analysis_version IS NULL OR a.analysis_version <> ?)
                          AND (a.risk_score >= ? OR c.liked_count >= ?)
                          AND (a.analysis_lock_token IS NULL OR a.analysis_locked_at < ?)
                        ORDER BY a.risk_score DESC, c.liked_count DESC, a.id
                        LIMIT ?
                    ) claimable
                )
                  AND (analysis_lock_token IS NULL OR analysis_locked_at < ?)
                """, token, Timestamp.from(now), maxAttempts, version,
                minimumRuleRiskScore, minimumLikes, Timestamp.from(staleBefore),
                Math.max(1, Math.min(limit, 20)), Timestamp.from(staleBefore));
        return jdbcTemplate.query("""
                SELECT a.id, a.post_id, a.source_comment_id, c.content,
                       c.liked_count, a.risk_score
                FROM xhs_comment_analysis_results a
                JOIN xhs_comments c
                  ON c.post_id = a.post_id AND c.source_comment_id = a.source_comment_id
                WHERE a.analysis_lock_token = ?
                ORDER BY a.risk_score DESC, c.liked_count DESC, a.id
                """, (rs, row) -> new XhsCommentAnalysisCandidate(
                        rs.getLong("id"), rs.getLong("post_id"),
                        rs.getString("source_comment_id"), rs.getString("content"),
                        rs.getLong("liked_count"), rs.getInt("risk_score"), token), token);
    }

    @Override
    public void save(XhsCommentAnalysisCandidate candidate, String version,
                     XhsCommentAssessment assessment, Instant analyzedAt) {
        jdbcTemplate.update("""
                UPDATE xhs_comment_analysis_results
                SET sentiment = ?, risk_score = ?, is_negative = ?,
                    analysis_method = 'LLM', analysis_version = ?, confidence = ?,
                    summary = ?, evidence_json = ?, analysis_status = 'SUCCEEDED',
                    error_message = NULL, analyzed_at = ?,
                    analysis_lock_token = NULL, analysis_locked_at = NULL
                WHERE id = ? AND analysis_lock_token = ?
                """, assessment.negative() ? "NEGATIVE" : "NEUTRAL",
                assessment.riskScore(), assessment.negative(), version,
                assessment.confidence(), assessment.summary(), json(assessment.evidence()),
                Timestamp.from(analyzedAt), candidate.analysisId(), candidate.claimToken());
    }

    @Override
    public void fail(List<XhsCommentAnalysisCandidate> candidates, int maxAttempts,
                     String message, Instant failedAt) {
        for (XhsCommentAnalysisCandidate candidate : candidates) {
            jdbcTemplate.update("""
                    UPDATE xhs_comment_analysis_results
                    SET attempt_count = attempt_count + 1,
                        analysis_status = CASE WHEN attempt_count + 1 >= ?
                            THEN 'FAILED' ELSE 'PENDING' END,
                        error_message = ?, analyzed_at = ?,
                        analysis_lock_token = NULL, analysis_locked_at = NULL
                    WHERE id = ? AND analysis_lock_token = ?
                    """, maxAttempts, truncate(message), Timestamp.from(failedAt),
                    candidate.analysisId(), candidate.claimToken());
        }
    }

    @Override
    public void recordExecution(String batchKey, String version, int commentCount,
                                XhsAnalysisLlmClient.Response response, String status,
                                String errorMessage, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO xhs_comment_analysis_executions(
                    batch_key, analysis_version, model_name, comment_count,
                    prompt_tokens, completion_tokens, total_tokens, duration_ms,
                    status, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batchKey, version, response == null ? "" : response.model(), commentCount,
                response == null ? 0 : response.promptTokens(),
                response == null ? 0 : response.completionTokens(),
                response == null ? 0 : response.totalTokens(),
                response == null ? 0 : response.durationMs(),
                status, truncateNullable(errorMessage), Timestamp.from(createdAt));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private String truncate(String value) {
        String safe = value == null || value.isBlank() ? "comment analysis failed" : value.strip();
        return safe.length() <= 2000 ? safe : safe.substring(0, 2000);
    }

    private String truncateNullable(String value) {
        return value == null || value.isBlank() ? null : truncate(value);
    }
}
