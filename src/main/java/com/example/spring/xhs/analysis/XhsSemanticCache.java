package com.example.spring.xhs.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Component
public class XhsSemanticCache {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public XhsSemanticCache(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Cached find(XhsAnalysisCandidate candidate, String version) {
        String hash = hash(candidate);
        try {
            List<CacheRow> values = jdbcTemplate.query("""
                    SELECT payload_json, model_name FROM xhs_semantic_cache
                    WHERE content_hash = ? AND analysis_version = ?
                    """, (rs, row) -> new CacheRow(rs.getString("payload_json"), rs.getString("model_name")),
                    hash, version);
            if (values.isEmpty()) {
                return null;
            }
            CacheRow value = values.get(0);
            return new Cached(objectMapper.readValue(value.payload(), XhsSemanticAssessment.class), value.model());
        } catch (DataAccessException | JsonProcessingException exception) {
            return null;
        }
    }

    public void save(XhsAnalysisCandidate candidate, String version, String model, XhsSemanticAssessment assessment) {
        try {
            String payload = objectMapper.writeValueAsString(assessment);
            Instant now = Instant.now();
            jdbcTemplate.update("""
                    INSERT INTO xhs_semantic_cache(content_hash, analysis_version, payload_json, model_name, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json), model_name = VALUES(model_name), updated_at = VALUES(updated_at)
                    """, hash(candidate), version, payload, model, Timestamp.from(now), Timestamp.from(now));
        } catch (DataAccessException | JsonProcessingException ignored) {
            // A cache outage must never make a valid analysis fail.
        }
    }

    public String hash(XhsAnalysisCandidate candidate) {
        String text = normalize(candidate.title()) + "\n" + normalize(candidate.content());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法计算舆情内容哈希", exception);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip();
    }

    public record Cached(XhsSemanticAssessment assessment, String model) {
    }

    private record CacheRow(String payload, String model) {
    }
}
