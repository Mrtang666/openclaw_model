package com.example.spring.wechat.knowledge.repository;

import com.example.spring.wechat.knowledge.model.KnowledgeDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 使用 MySQL 保存知识库文档元信息和操作日志。
 */
@Repository
public class MySqlKnowledgeRepository implements KnowledgeRepository {

    private final JdbcTemplate jdbcTemplate;

    public MySqlKnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<KnowledgeDocument> findActiveByHash(String sessionKey, String contentHash) {
        return jdbcTemplate.query(
                        """
                                SELECT id, session_key, title, source_type, source_url, tags, content_hash,
                                       chunk_count, created_at, updated_at, deleted
                                FROM wechat_knowledge_document
                                WHERE session_key = ? AND content_hash = ? AND deleted = 0
                                ORDER BY id DESC
                                LIMIT 1
                                """,
                        this::mapDocument,
                        safe(sessionKey),
                        safe(contentHash))
                .stream()
                .findFirst();
    }

    @Override
    public KnowledgeDocument createDocument(
            String sessionKey,
            String title,
            String sourceType,
            String sourceUrl,
            String tags,
            String contentHash,
            int chunkCount,
            Instant now) {
        Instant time = now == null ? Instant.now() : now;
        long id = insertAndReadKey(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO wechat_knowledge_document
                            (session_key, title, source_type, source_url, tags, content_hash, chunk_count, created_at, updated_at, deleted)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, safe(sessionKey));
            statement.setString(2, safe(title));
            statement.setString(3, safe(sourceType));
            statement.setString(4, safe(sourceUrl));
            statement.setString(5, safe(tags));
            statement.setString(6, safe(contentHash));
            statement.setInt(7, Math.max(0, chunkCount));
            statement.setTimestamp(8, Timestamp.from(time));
            statement.setTimestamp(9, Timestamp.from(time));
            return statement;
        });
        return new KnowledgeDocument(id, safe(sessionKey), safe(title), safe(sourceType), safe(sourceUrl),
                safe(tags), safe(contentHash), Math.max(0, chunkCount), time, time, false);
    }

    @Override
    public List<KnowledgeDocument> listDocuments(String sessionKey, String keyword, int limit) {
        String text = safe(keyword);
        if (text.isBlank()) {
            return jdbcTemplate.query(
                    """
                            SELECT id, session_key, title, source_type, source_url, tags, content_hash,
                                   chunk_count, created_at, updated_at, deleted
                            FROM wechat_knowledge_document
                            WHERE session_key = ? AND deleted = 0
                            ORDER BY updated_at DESC, id DESC
                            LIMIT ?
                            """,
                    this::mapDocument,
                    safe(sessionKey),
                    Math.max(1, limit));
        }
        String like = "%" + text + "%";
        return jdbcTemplate.query(
                """
                        SELECT id, session_key, title, source_type, source_url, tags, content_hash,
                               chunk_count, created_at, updated_at, deleted
                        FROM wechat_knowledge_document
                        WHERE session_key = ? AND deleted = 0 AND (title LIKE ? OR tags LIKE ? OR source_url LIKE ?)
                        ORDER BY updated_at DESC, id DESC
                        LIMIT ?
                        """,
                this::mapDocument,
                safe(sessionKey),
                like,
                like,
                like,
                Math.max(1, limit));
    }

    @Override
    public Optional<KnowledgeDocument> findDocument(String sessionKey, long documentId) {
        return jdbcTemplate.query(
                        """
                                SELECT id, session_key, title, source_type, source_url, tags, content_hash,
                                       chunk_count, created_at, updated_at, deleted
                                FROM wechat_knowledge_document
                                WHERE session_key = ? AND id = ? AND deleted = 0
                                """,
                        this::mapDocument,
                        safe(sessionKey),
                        documentId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean softDelete(String sessionKey, long documentId, Instant now) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE wechat_knowledge_document
                        SET deleted = 1, updated_at = ?
                        WHERE session_key = ? AND id = ? AND deleted = 0
                        """,
                Timestamp.from(now == null ? Instant.now() : now),
                safe(sessionKey),
                documentId);
        return updated > 0;
    }

    @Override
    public boolean updateTitle(String sessionKey, long documentId, String title, Instant now) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE wechat_knowledge_document
                        SET title = ?, updated_at = ?
                        WHERE session_key = ? AND id = ? AND deleted = 0
                        """,
                safe(title),
                Timestamp.from(now == null ? Instant.now() : now),
                safe(sessionKey),
                documentId);
        return updated > 0;
    }

    @Override
    public boolean updateTags(String sessionKey, long documentId, String tags, Instant now) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE wechat_knowledge_document
                        SET tags = ?, updated_at = ?
                        WHERE session_key = ? AND id = ? AND deleted = 0
                        """,
                safe(tags),
                Timestamp.from(now == null ? Instant.now() : now),
                safe(sessionKey),
                documentId);
        return updated > 0;
    }

    @Override
    public void log(String sessionKey, String operation, Long documentId, String queryText, String resultSummary, Instant now) {
        jdbcTemplate.update(
                """
                        INSERT INTO wechat_knowledge_log
                        (session_key, operation, document_id, query_text, result_summary, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                safe(sessionKey),
                safe(operation),
                documentId,
                safe(queryText),
                truncate(resultSummary, 2000),
                Timestamp.from(now == null ? Instant.now() : now));
    }

    private KnowledgeDocument mapDocument(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new KnowledgeDocument(
                resultSet.getLong("id"),
                resultSet.getString("session_key"),
                resultSet.getString("title"),
                resultSet.getString("source_type"),
                resultSet.getString("source_url"),
                resultSet.getString("tags"),
                resultSet.getString("content_hash"),
                resultSet.getInt("chunk_count"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getBoolean("deleted"));
    }

    private long insertAndReadKey(PreparedStatementCreator creator) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(creator, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("数据库未返回知识文档主键");
        }
        return key.longValue();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private String truncate(String value, int maxLength) {
        String text = safe(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
