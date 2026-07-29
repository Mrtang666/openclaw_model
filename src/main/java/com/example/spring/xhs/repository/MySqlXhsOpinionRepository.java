package com.example.spring.xhs.repository;

import com.example.spring.xhs.model.XhsCommentImport;
import com.example.spring.xhs.model.XhsPostImport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

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
