package com.example.spring.xhs.analysis;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public class XhsAnalysisTelemetry {

    private final JdbcTemplate jdbcTemplate;

    public XhsAnalysisTelemetry(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void model(long postId, String version, XhsAnalysisLlmClient.Response response) {
        insert(postId, version, response.model(), "MODEL", response.promptTokens(),
                response.completionTokens(), response.totalTokens(), response.durationMs(), null);
    }

    public void fallback(long postId, String version, String model, long durationMs, Throwable error) {
        insert(postId, version, model, "FALLBACK", 0, 0, 0, durationMs, safeMessage(error));
    }

    public void cache(long postId, String version, String model) {
        insert(postId, version, model, "CACHE", 0, 0, 0, 0, null);
    }

    private void insert(long postId, String version, String model, String status, int promptTokens,
                        int completionTokens, int totalTokens, long durationMs, String error) {
        jdbcTemplate.update("""
                INSERT INTO xhs_analysis_executions(
                    post_id, analysis_version, model_name, status, prompt_tokens,
                    completion_tokens, total_tokens, duration_ms, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, postId, version, model, status, promptTokens, completionTokens, totalTokens,
                Math.max(0, durationMs), error, Timestamp.from(Instant.now()));
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? "未知模型错误" : error.getMessage();
        String value = message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
        return value.substring(0, Math.min(value.length(), 1000));
    }
}
