package com.example.spring.xhs.repository;

import com.example.spring.xhs.analysis.XhsImageAnalysisCandidate;
import com.example.spring.xhs.analysis.XhsImageAssessment;
import com.example.spring.wechat.image.client.ImageUnderstandingClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MySqlXhsImageAnalysisRepository implements XhsImageAnalysisRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MySqlXhsImageAnalysisRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<XhsImageAnalysisCandidate> claimPending(
            String version, int limit, int maxAttempts, Instant staleBefore) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE xhs_post_images
                SET analysis_lock_token = ?, analysis_locked_at = ?
                WHERE id IN (
                    SELECT id FROM (
                        SELECT id FROM xhs_post_images
                        WHERE attempt_count < ?
                          AND (analysis_status = 'PENDING'
                               OR analysis_version IS NULL OR analysis_version <> ?)
                          AND (analysis_lock_token IS NULL OR analysis_locked_at < ?)
                        ORDER BY last_collected_at, id LIMIT ?
                    ) claimable
                )
                  AND (analysis_lock_token IS NULL OR analysis_locked_at < ?)
                """, token, Timestamp.from(now), maxAttempts, version, Timestamp.from(staleBefore),
                Math.max(1, Math.min(limit, 20)), Timestamp.from(staleBefore));
        return jdbcTemplate.query("""
                SELECT i.id, i.post_id, i.image_hash, i.image_url, p.title, p.content
                FROM xhs_post_images i
                JOIN xhs_posts p ON p.id = i.post_id
                WHERE i.analysis_lock_token = ?
                ORDER BY i.last_collected_at, i.id
                """, (rs, row) -> new XhsImageAnalysisCandidate(
                        rs.getLong("id"), rs.getLong("post_id"), rs.getString("image_hash"),
                        rs.getString("image_url"), rs.getString("title"), rs.getString("content"), token),
                token);
    }

    @Override
    public boolean reuseCached(
            XhsImageAnalysisCandidate candidate, String version, Instant analyzedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE xhs_post_images target
                JOIN xhs_post_images cached
                  ON cached.image_hash = target.image_hash
                 AND cached.analysis_version = ?
                 AND cached.analysis_status = 'SUCCEEDED'
                 AND cached.id <> target.id
                SET target.analysis_status = 'SUCCEEDED',
                    target.analysis_version = cached.analysis_version,
                    target.sentiment = cached.sentiment,
                    target.risk_score = cached.risk_score,
                    target.contains_product = cached.contains_product,
                    target.summary = cached.summary,
                    target.evidence_json = cached.evidence_json,
                    target.error_message = NULL,
                    target.analyzed_at = ?,
                    target.analysis_lock_token = NULL,
                    target.analysis_locked_at = NULL
                WHERE target.id = ? AND target.analysis_lock_token = ?
                """, version, Timestamp.from(analyzedAt), candidate.imageId(), candidate.claimToken());
        return updated > 0;
    }

    @Override
    public void save(XhsImageAnalysisCandidate candidate, String version,
                     XhsImageAssessment assessment, Instant analyzedAt) {
        jdbcTemplate.update("""
                UPDATE xhs_post_images
                SET analysis_status = 'SUCCEEDED', analysis_version = ?, sentiment = ?,
                    risk_score = ?, contains_product = ?, summary = ?, evidence_json = ?,
                    error_message = NULL, analyzed_at = ?,
                    analysis_lock_token = NULL, analysis_locked_at = NULL
                WHERE id = ? AND analysis_lock_token = ?
                """, version, assessment.negative() ? "NEGATIVE" : "NEUTRAL",
                assessment.riskScore(), assessment.containsProduct(), assessment.summary(),
                json(assessment.evidence()), Timestamp.from(analyzedAt),
                candidate.imageId(), candidate.claimToken());
    }

    @Override
    public void fail(XhsImageAnalysisCandidate candidate, int maxAttempts,
                     String message, Instant failedAt) {
        jdbcTemplate.update("""
                UPDATE xhs_post_images
                SET attempt_count = attempt_count + 1,
                    analysis_status = CASE WHEN attempt_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    error_message = ?, analyzed_at = ?,
                    analysis_lock_token = NULL, analysis_locked_at = NULL
                WHERE id = ? AND analysis_lock_token = ?
                """, maxAttempts, truncate(message), Timestamp.from(failedAt),
                candidate.imageId(), candidate.claimToken());
    }

    @Override
    public void recordExecution(
            XhsImageAnalysisCandidate candidate, String version,
            ImageUnderstandingClient.Response response, String status,
            String errorMessage, long durationMs, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO xhs_image_analysis_executions(
                    image_id, post_id, analysis_version, model_name,
                    prompt_tokens, completion_tokens, total_tokens, duration_ms,
                    status, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, candidate.imageId(), candidate.postId(), version,
                response == null ? "" : response.model(),
                response == null ? 0 : response.promptTokens(),
                response == null ? 0 : response.completionTokens(),
                response == null ? 0 : response.totalTokens(),
                response == null ? Math.max(0, durationMs) : response.durationMs(),
                status, errorMessage == null || errorMessage.isBlank() ? null : truncate(errorMessage),
                Timestamp.from(createdAt));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private String truncate(String value) {
        String safe = value == null || value.isBlank() ? "image analysis failed" : value.strip();
        return safe.length() <= 2000 ? safe : safe.substring(0, 2000);
    }
}
