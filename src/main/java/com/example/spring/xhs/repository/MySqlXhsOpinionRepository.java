package com.example.spring.xhs.repository;

import com.example.spring.xhs.model.XhsCommentImport;
import com.example.spring.xhs.model.XhsPostImport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Repository
public class MySqlXhsOpinionRepository implements XhsOpinionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MySqlXhsOpinionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public long ensureProject(String projectKey, String projectName, Instant now) {
        Instant time = now == null ? Instant.now() : now;
        String key = required(projectKey, "projectKey");
        String name = projectName == null || projectName.isBlank() ? key : projectName.strip();
        jdbcTemplate.update("""
                        INSERT INTO xhs_monitor_projects(project_key, name, status, created_at, updated_at)
                        VALUES (?, ?, 'ACTIVE', ?, ?)
                        ON DUPLICATE KEY UPDATE name = VALUES(name), updated_at = VALUES(updated_at)
                        """,
                key, name, Timestamp.from(time), Timestamp.from(time));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM xhs_monitor_projects WHERE project_key = ?",
                Long.class,
                key);
    }

    @Override
    public long upsertPost(long projectId, XhsPostImport post) {
        jdbcTemplate.update("""
                        INSERT INTO xhs_posts(
                            project_id, source_type, source_post_id, source_url, access_url,
                            access_url_refreshed_at, author_key,
                            title, content, note_type, tags_json, published_at,
                            first_collected_at, last_collected_at, raw_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            source_url = VALUES(source_url),
                            access_url = COALESCE(VALUES(access_url), access_url),
                            access_url_refreshed_at = COALESCE(VALUES(access_url_refreshed_at), access_url_refreshed_at),
                            author_key = VALUES(author_key),
                            title = VALUES(title),
                            content = VALUES(content),
                            note_type = VALUES(note_type),
                            tags_json = VALUES(tags_json),
                            published_at = COALESCE(VALUES(published_at), published_at),
                            last_collected_at = VALUES(last_collected_at),
                            raw_json = VALUES(raw_json)
                        """,
                projectId,
                post.sourceType().name(),
                post.sourcePostId(),
                post.sourceUrl(),
                nullable(post.accessUrl()),
                post.accessUrl().isBlank() ? null : Timestamp.from(post.collectedAt()),
                post.authorKey(),
                post.title(),
                post.content(),
                post.noteType(),
                json(post.tags()),
                timestamp(post.publishedAt()),
                Timestamp.from(post.collectedAt()),
                Timestamp.from(post.collectedAt()),
                post.rawJson());
        return jdbcTemplate.queryForObject(
                """
                        SELECT id FROM xhs_posts
                        WHERE project_id = ? AND source_type = ? AND source_post_id = ?
                        """,
                Long.class,
                projectId,
                post.sourceType().name(),
                post.sourcePostId());
    }

    @Override
    public void upsertComment(long postId, XhsCommentImport comment) {
        jdbcTemplate.update("""
                        INSERT INTO xhs_comments(
                            post_id, source_comment_id, parent_comment_id, author_key, content,
                            liked_count, published_at, first_collected_at, last_collected_at, raw_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            parent_comment_id = VALUES(parent_comment_id),
                            author_key = VALUES(author_key),
                            content = VALUES(content),
                            liked_count = VALUES(liked_count),
                            published_at = COALESCE(VALUES(published_at), published_at),
                            last_collected_at = VALUES(last_collected_at),
                            raw_json = VALUES(raw_json)
                        """,
                postId,
                comment.sourceCommentId(),
                nullable(comment.parentCommentId()),
                comment.authorKey(),
                comment.content(),
                comment.likedCount(),
                timestamp(comment.publishedAt()),
                Timestamp.from(comment.collectedAt()),
                Timestamp.from(comment.collectedAt()),
                comment.rawJson());
    }

    @Override
    public void saveMetricSnapshot(long postId, XhsPostImport post) {
        jdbcTemplate.update("""
                        INSERT INTO xhs_metric_snapshots(
                            post_id, snapshot_at, liked_count, collected_count, comment_count, share_count)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            liked_count = VALUES(liked_count),
                            collected_count = VALUES(collected_count),
                            comment_count = VALUES(comment_count),
                            share_count = VALUES(share_count)
                        """,
                postId,
                Timestamp.from(post.collectedAt()),
                post.metrics().likedCount(),
                post.metrics().collectedCount(),
                post.metrics().commentCount(),
                post.metrics().shareCount());
    }

    @Override
    public void recordSearchHit(long postId, String jobKey, String keyword, Instant hitAt) {
        if (jobKey == null || jobKey.isBlank()) {
            return;
        }
        Instant now = hitAt == null ? Instant.now() : hitAt;
        jdbcTemplate.update("""
                INSERT INTO xhs_post_search_hits(
                    post_id, search_execution_id, keyword_value, first_hit_at, last_hit_at)
                SELECT ?, e.id, ?, ?, ? FROM xhs_search_executions e WHERE e.job_key = ?
                ON DUPLICATE KEY UPDATE last_hit_at = VALUES(last_hit_at)
                """, postId, keyword == null ? "" : keyword.strip(), Timestamp.from(now),
                Timestamp.from(now), jobKey.strip());
    }

    @Override
    public void updateCollectionCompleteness(long postId, long expectedCommentCount,
                                             int collectedCommentCount, int discoveredImageCount,
                                             Instant collectedAt) {
        long expected = Math.max(0, expectedCommentCount);
        int collected = Math.max(0, collectedCommentCount);
        int images = Math.max(0, discoveredImageCount);
        String commentsStatus = expected == 0 || collected >= expected ? "FULL" : "PARTIAL";
        String imagesStatus = images > 0 ? "DISCOVERED" : "NOT_REQUESTED";
        Instant now = collectedAt == null ? Instant.now() : collectedAt;
        jdbcTemplate.update("""
                INSERT INTO xhs_post_collection_completeness(
                    post_id, detail_status, comments_status, images_status,
                    expected_comment_count, collected_comment_count, discovered_image_count,
                    last_collected_at, updated_at)
                VALUES (?, 'FULL', ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    detail_status = 'FULL', comments_status = VALUES(comments_status),
                    images_status = VALUES(images_status),
                    expected_comment_count = VALUES(expected_comment_count),
                    collected_comment_count = VALUES(collected_comment_count),
                    discovered_image_count = VALUES(discovered_image_count),
                    last_collected_at = VALUES(last_collected_at), updated_at = VALUES(updated_at)
                """, postId, commentsStatus, imagesStatus, expected, collected, images,
                Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public void recordCommentAnalysis(long postId, String sourceCommentId, String sentiment,
                                      int riskScore, boolean negative, Instant analyzedAt) {
        if (sourceCommentId == null || sourceCommentId.isBlank()) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO xhs_comment_analysis_results(
                    post_id, source_comment_id, sentiment, risk_score, is_negative,
                    analysis_method, analysis_status, analyzed_at)
                VALUES (?, ?, ?, ?, ?, 'RULE', ?, ?)
                ON DUPLICATE KEY UPDATE
                    sentiment = IF(analysis_method = 'RULE', VALUES(sentiment), sentiment),
                    risk_score = IF(analysis_method = 'RULE', VALUES(risk_score), risk_score),
                    is_negative = IF(analysis_method = 'RULE', VALUES(is_negative), is_negative),
                    analysis_status = IF(analysis_method = 'RULE',
                        IF(VALUES(is_negative), 'PENDING', 'SKIPPED'), analysis_status),
                    analyzed_at = IF(analysis_method = 'RULE', VALUES(analyzed_at), analyzed_at)
                """, postId, sourceCommentId.strip(),
                sentiment == null || sentiment.isBlank() ? "NEUTRAL" : sentiment,
                Math.max(0, Math.min(riskScore, 100)), negative,
                negative ? "PENDING" : "SKIPPED",
                Timestamp.from(analyzedAt == null ? Instant.now() : analyzedAt));
    }

    @Override
    public void recordPostImage(long postId, int imageOrder, String imageUrl, Instant collectedAt) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        Instant now = collectedAt == null ? Instant.now() : collectedAt;
        jdbcTemplate.update("""
                INSERT INTO xhs_post_images(
                    post_id, image_hash, image_url, image_order, analysis_status,
                    first_collected_at, last_collected_at)
                VALUES (?, ?, ?, ?, 'PENDING', ?, ?)
                ON DUPLICATE KEY UPDATE image_url = VALUES(image_url),
                    image_order = VALUES(image_order),
                    last_collected_at = VALUES(last_collected_at)
                """, postId, sha256(imageUrl), imageUrl, Math.max(0, imageOrder),
                Timestamp.from(now), Timestamp.from(now));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化小红书数据", exception);
        }
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.strip();
    }
}
