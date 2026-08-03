package com.example.spring.wechat.context;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Repository
public class MySqlMemoryGraphRepository implements MemoryGraphRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public MySqlMemoryGraphRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    MySqlMemoryGraphRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public MemoryGraphNode createNode(MemoryGraphNodeDraft draft) {
        Instant now = Instant.now(clock);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(insertNodeStatement(draft, now), keyHolder);
        Number key = keyHolder.getKey();
        long id = key == null ? 0L : key.longValue();
        return new MemoryGraphNode(
                id,
                draft.sessionKey(),
                draft.conversationId(),
                draft.nodeType(),
                draft.topicKey(),
                draft.title(),
                draft.content(),
                draft.summary(),
                draft.importanceScore(),
                draft.relevanceScore(),
                draft.confidenceScore(),
                draft.sourceMessageStartId(),
                draft.sourceMessageEndId(),
                draft.sourceType(),
                draft.sourceRef(),
                draft.tags(),
                now,
                now,
                draft.expiresAt(),
                false);
    }

    @Override
    public MemoryGraphEdge createEdge(MemoryGraphEdgeDraft draft) {
        Instant now = Instant.now(clock);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO memory_graph_edges
                            (session_key, source_node_id, target_node_id, edge_type, weight, created_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, draft.sessionKey());
            statement.setLong(2, draft.sourceNodeId());
            statement.setLong(3, draft.targetNodeId());
            statement.setString(4, draft.edgeType().name());
            statement.setDouble(5, draft.weight());
            statement.setTimestamp(6, Timestamp.from(now));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return new MemoryGraphEdge(
                key == null ? 0L : key.longValue(),
                draft.sessionKey(),
                draft.sourceNodeId(),
                draft.targetNodeId(),
                draft.edgeType(),
                draft.weight(),
                now);
    }

    @Override
    public List<MemoryGraphNode> findRecentNodes(String sessionKey, MemoryNodeType nodeType, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT *
                        FROM memory_graph_nodes
                        WHERE session_key = ? AND node_type = ? AND deleted = 0
                        ORDER BY created_at DESC, id DESC
                        LIMIT ?
                        """,
                this::mapNode,
                clean(sessionKey),
                nodeType.name(),
                safeLimit(limit));
    }

    @Override
    public List<MemoryGraphNode> findRecentNodesByTopic(
            String sessionKey,
            MemoryNodeType nodeType,
            String topicKey,
            int limit) {
        return jdbcTemplate.query(
                """
                        SELECT *
                        FROM memory_graph_nodes
                        WHERE session_key = ? AND node_type = ? AND topic_key = ? AND deleted = 0
                        ORDER BY relevance_score DESC, created_at DESC, id DESC
                        LIMIT ?
                        """,
                this::mapNode,
                clean(sessionKey),
                nodeType.name(),
                clean(topicKey),
                safeLimit(limit));
    }

    @Override
    public List<MemoryGraphEdge> findOutgoingEdges(long sourceNodeId, MemoryEdgeType edgeType) {
        return jdbcTemplate.query(
                """
                        SELECT *
                        FROM memory_graph_edges
                        WHERE source_node_id = ? AND edge_type = ?
                        ORDER BY weight DESC, id DESC
                        """,
                this::mapEdge,
                sourceNodeId,
                edgeType.name());
    }

    @Override
    public void softDeleteNode(long nodeId) {
        jdbcTemplate.update(
                "UPDATE memory_graph_nodes SET deleted = 1, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now(clock)),
                nodeId);
    }

    private PreparedStatementCreator insertNodeStatement(MemoryGraphNodeDraft draft, Instant now) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO memory_graph_nodes
                            (session_key, conversation_id, node_type, topic_key, title, content, summary,
                             importance_score, relevance_score, confidence_score, source_message_start_id,
                             source_message_end_id, source_type, source_ref, tags, created_at, updated_at, expires_at, deleted)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, draft.sessionKey());
            setLong(statement, 2, draft.conversationId());
            statement.setString(3, draft.nodeType().name());
            statement.setString(4, emptyToNull(draft.topicKey()));
            statement.setString(5, draft.title());
            statement.setString(6, draft.content());
            statement.setString(7, emptyToNull(draft.summary()));
            statement.setDouble(8, draft.importanceScore());
            statement.setDouble(9, draft.relevanceScore());
            statement.setDouble(10, draft.confidenceScore());
            setLong(statement, 11, draft.sourceMessageStartId());
            setLong(statement, 12, draft.sourceMessageEndId());
            statement.setString(13, emptyToNull(draft.sourceType()));
            statement.setString(14, emptyToNull(draft.sourceRef()));
            statement.setString(15, emptyToNull(draft.tags()));
            statement.setTimestamp(16, Timestamp.from(now));
            statement.setTimestamp(17, Timestamp.from(now));
            setTimestamp(statement, 18, draft.expiresAt());
            return statement;
        };
    }

    private MemoryGraphNode mapNode(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryGraphNode(
                rs.getLong("id"),
                rs.getString("session_key"),
                nullableLong(rs, "conversation_id"),
                MemoryNodeType.valueOf(rs.getString("node_type")),
                rs.getString("topic_key"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("summary"),
                rs.getDouble("importance_score"),
                rs.getDouble("relevance_score"),
                rs.getDouble("confidence_score"),
                nullableLong(rs, "source_message_start_id"),
                nullableLong(rs, "source_message_end_id"),
                rs.getString("source_type"),
                rs.getString("source_ref"),
                rs.getString("tags"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")),
                instant(rs.getTimestamp("expires_at")),
                rs.getBoolean("deleted"));
    }

    private MemoryGraphEdge mapEdge(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryGraphEdge(
                rs.getLong("id"),
                rs.getString("session_key"),
                rs.getLong("source_node_id"),
                rs.getLong("target_node_id"),
                MemoryEdgeType.valueOf(rs.getString("edge_type")),
                rs.getDouble("weight"),
                instant(rs.getTimestamp("created_at")));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    private void setTimestamp(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }

    private String emptyToNull(String value) {
        String text = clean(value);
        return text.isBlank() ? null : text;
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private int safeLimit(int limit) {
        return limit <= 0 ? 10 : Math.min(limit, 100);
    }
}
