package com.example.spring.wechat.memory.service;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.memory.config.WechatMemoryProperties;
import com.example.spring.wechat.memory.fallback.InMemoryWechatMemoryFallback;
import com.example.spring.wechat.memory.model.ConversationTurn;
import com.example.spring.wechat.memory.model.WechatConversationMemory;
import com.example.spring.wechat.memory.model.WechatMemorySession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 微信上下文记忆的 MySQL 实现。
 *
 * <p>消息正文、会话状态、明确偏好和工具执行记录分别保存，避免将所有信息塞进单一 JSON。</p>
 */
@Service
@Primary
public class MySqlWechatMemoryService implements WechatMemoryService {

    private static final Logger log = LoggerFactory.getLogger(MySqlWechatMemoryService.class);
    private static final String CHANNEL_WECHAT = "WECHAT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CLOSED = "CLOSED";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final WechatMemoryProperties properties;
    private final InMemoryWechatMemoryFallback fallback;
    private final ChatService chatService;

    public MySqlWechatMemoryService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            WechatMemoryProperties properties,
            InMemoryWechatMemoryFallback fallback,
            ChatService chatService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.fallback = fallback;
        this.chatService = chatService;
    }

    @Override
    @Transactional
    public WechatMemorySession open(String wechatUserId, Instant now) {
        Instant time = safeTime(now);
        try {
            return openPersistent(safeUserId(wechatUserId), time);
        } catch (DataAccessException exception) {
            log.warn("MySQL 上下文记忆不可用，改用进程内存，userId={}, error={}",
                    safeUserId(wechatUserId), rootMessage(exception));
            return fallback.open(wechatUserId, time);
        }
    }

    @Override
    @Transactional
    public boolean acceptIncoming(
            String wechatUserId,
            String sourceMessageId,
            String content,
            String contentType,
            Instant now) {
        Instant time = safeTime(now);
        try {
            WechatMemorySession session = openPersistent(safeUserId(wechatUserId), time);
            if (sourceMessageId == null || sourceMessageId.isBlank()) {
                insertMessage(session.conversationId(), null, "USER", content, contentType, time);
                return true;
            }
            try {
                insertMessage(session.conversationId(), sourceMessageId.strip(), "USER", content, contentType, time);
                return true;
            } catch (DuplicateKeyException exception) {
                return false;
            }
        } catch (DataAccessException exception) {
            log.warn("MySQL 入站消息记录失败，改用进程内存，userId={}, error={}",
                    safeUserId(wechatUserId), rootMessage(exception));
            return fallback.acceptIncoming(wechatUserId, sourceMessageId, content, contentType, time);
        }
    }

    @Override
    public WechatConversationMemory memoryFor(String wechatUserId) {
        return open(wechatUserId, Instant.now()).memory();
    }

    @Override
    @Transactional
    public void saveMemory(String wechatUserId, WechatConversationMemory memory, Instant now) {
        if (memory == null) {
            return;
        }
        Instant time = safeTime(now);
        try {
            WechatMemorySession session = openPersistent(safeUserId(wechatUserId), time);
            String json = objectMapper.writeValueAsString(memory.state());
            int updated = jdbcTemplate.update(
                    """
                            UPDATE conversation_states
                            SET state_json = ?, version = version + 1, updated_at = ?
                            WHERE conversation_id = ?
                            """,
                    json,
                    Timestamp.from(time),
                    session.conversationId());
            if (updated == 0) {
                jdbcTemplate.update(
                        """
                                INSERT INTO conversation_states
                                (conversation_id, state_json, version, updated_at)
                                VALUES (?, ?, 1, ?)
                                """,
                        session.conversationId(),
                        json,
                        Timestamp.from(time));
            }
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("MySQL 会话状态保存失败，改用进程内存，userId={}, error={}",
                    safeUserId(wechatUserId), rootMessage(exception));
            fallback.saveMemory(wechatUserId, memory, time);
        }
    }

    @Override
    @Transactional
    public void recordAssistantMessage(String wechatUserId, String content, String contentType, Instant now) {
        if (content == null || content.isBlank()) {
            return;
        }
        Instant time = safeTime(now);
        try {
            WechatMemorySession session = openPersistent(safeUserId(wechatUserId), time);
            insertMessage(session.conversationId(), null, "ASSISTANT", content, contentType, time);
        } catch (DataAccessException exception) {
            log.warn("MySQL 助手消息保存失败，改用进程内存，userId={}, error={}",
                    safeUserId(wechatUserId), rootMessage(exception));
            fallback.recordAssistantMessage(wechatUserId, content, contentType, time);
        }
    }

    @Override
    @Transactional
    public void recordToolExecution(
            String wechatUserId,
            String toolName,
            Map<String, String> arguments,
            String resultSummary,
            String status,
            Instant now) {
        Instant time = safeTime(now);
        try {
            WechatMemorySession session = openPersistent(safeUserId(wechatUserId), time);
            String argumentsJson = objectMapper.writeValueAsString(arguments == null ? Map.of() : arguments);
            jdbcTemplate.update(
                    """
                            INSERT INTO tool_execution_logs
                            (conversation_id, tool_name, arguments_json, result_summary, status, created_at, expires_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    session.conversationId(),
                    textOrEmpty(toolName),
                    argumentsJson,
                    truncate(resultSummary, 2_000),
                    textOrEmpty(status),
                    Timestamp.from(time),
                    Timestamp.from(time.plusSeconds(properties.rawRetentionDays() * 86_400L)));
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("MySQL 工具日志保存失败，改用进程内存，userId={}, error={}",
                    safeUserId(wechatUserId), rootMessage(exception));
            fallback.recordToolExecution(wechatUserId, toolName, arguments, resultSummary, status, time);
        }
    }

    @Override
    @Transactional
    public void maintainConversationSummaries(Instant now) {
        Instant time = safeTime(now);
        try {
            closeIdleConversationsAfterSummary(time);
            createRollingSummaries(time);
        } catch (DataAccessException exception) {
            log.warn("微信会话摘要维护失败，error={}", rootMessage(exception));
        }
    }

    @Override
    public Optional<String> explicitPreference(String wechatUserId, String preferenceKey) {
        if (preferenceKey == null || preferenceKey.isBlank()) {
            return Optional.empty();
        }
        try {
            Long userId = findUserId(safeUserId(wechatUserId));
            if (userId == null) {
                return Optional.empty();
            }
            List<String> values = jdbcTemplate.query(
                    """
                            SELECT preference_value_json
                            FROM user_preferences
                            WHERE user_id = ? AND preference_key = ?
                            """,
                    (resultSet, rowNumber) -> resultSet.getString(1),
                    userId,
                    preferenceKey.strip());
            return values.stream().findFirst();
        } catch (DataAccessException exception) {
            log.warn("MySQL 用户偏好读取失败，改用进程内存，userId={}, error={}",
                    safeUserId(wechatUserId), rootMessage(exception));
            return fallback.explicitPreference(wechatUserId, preferenceKey);
        }
    }

    @Override
    @Transactional
    public void saveExplicitPreference(
            String wechatUserId,
            String preferenceKey,
            String valueJson,
            String source,
            Instant now) {
        if (preferenceKey == null || preferenceKey.isBlank() || valueJson == null || valueJson.isBlank()) {
            return;
        }
        Instant time = safeTime(now);
        try {
            WechatMemorySession session = openPersistent(safeUserId(wechatUserId), time);
            int updated = jdbcTemplate.update(
                    """
                            UPDATE user_preferences
                            SET preference_value_json = ?, source = ?, updated_at = ?
                            WHERE user_id = ? AND preference_key = ?
                            """,
                    valueJson,
                    textOrEmpty(source),
                    Timestamp.from(time),
                    session.userId(),
                    preferenceKey.strip());
            if (updated == 0) {
                jdbcTemplate.update(
                        """
                                INSERT INTO user_preferences
                                (user_id, preference_key, preference_value_json, source, updated_at)
                                VALUES (?, ?, ?, ?, ?)
                                """,
                        session.userId(),
                        preferenceKey.strip(),
                        valueJson,
                        textOrEmpty(source),
                        Timestamp.from(time));
            }
        } catch (DataAccessException exception) {
            log.warn("MySQL 用户偏好保存失败，改用进程内存，userId={}, error={}",
                    safeUserId(wechatUserId), rootMessage(exception));
            fallback.saveExplicitPreference(wechatUserId, preferenceKey, valueJson, source, time);
        }
    }

    private WechatMemorySession openPersistent(String wechatUserId, Instant now) {
        long userId = findOrCreateUser(wechatUserId, now);
        closeExpiredConversationsAfterSummary(userId, now);
        Long activeConversationId = findActiveConversationId(userId);
        long conversationId = activeConversationId == null
                ? createConversation(userId, now)
                : activeConversationId;
        touchConversation(conversationId, now);
        return new WechatMemorySession(userId, conversationId, loadMemory(userId, conversationId));
    }

    private long findOrCreateUser(String wechatUserId, Instant now) {
        Long existing = findUserId(wechatUserId);
        if (existing != null) {
            jdbcTemplate.update(
                    "UPDATE users SET updated_at = ? WHERE id = ?",
                    Timestamp.from(now),
                    existing);
            return existing;
        }
        try {
            return insertAndReadKey(
                    connection -> {
                        PreparedStatement statement = connection.prepareStatement(
                                """
                                        INSERT INTO users (wechat_user_id, created_at, updated_at)
                                        VALUES (?, ?, ?)
                                        """,
                                Statement.RETURN_GENERATED_KEYS);
                        statement.setString(1, wechatUserId);
                        statement.setTimestamp(2, Timestamp.from(now));
                        statement.setTimestamp(3, Timestamp.from(now));
                        return statement;
                    });
        } catch (DuplicateKeyException exception) {
            Long concurrent = findUserId(wechatUserId);
            if (concurrent == null) {
                throw exception;
            }
            return concurrent;
        }
    }

    private Long findUserId(String wechatUserId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM users WHERE wechat_user_id = ?",
                (resultSet, rowNumber) -> resultSet.getLong(1),
                wechatUserId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void closeExpiredConversationsAfterSummary(long userId, Instant now) {
        List<Long> conversationIds = jdbcTemplate.query(
                """
                        SELECT id
                        FROM conversations
                        WHERE user_id = ? AND channel = ? AND status = ? AND last_active_at <= ?
                        ORDER BY last_active_at, id
                        """,
                (resultSet, rowNumber) -> resultSet.getLong(1),
                userId,
                CHANNEL_WECHAT,
                STATUS_ACTIVE,
                Timestamp.from(now.minusSeconds(properties.sessionIdleMinutes() * 60L)));
        for (Long conversationId : conversationIds) {
            if (conversationId != null && summarizeConversation(conversationId, now)) {
                closeConversation(conversationId, now);
            }
        }
    }

    private Long findActiveConversationId(long userId) {
        List<Long> ids = jdbcTemplate.query(
                """
                        SELECT id
                        FROM conversations
                        WHERE user_id = ? AND channel = ? AND status = ?
                        ORDER BY last_active_at DESC, id DESC
                        LIMIT 1
                        """,
                (resultSet, rowNumber) -> resultSet.getLong(1),
                userId,
                CHANNEL_WECHAT,
                STATUS_ACTIVE);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private long createConversation(long userId, Instant now) {
        return insertAndReadKey(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            """
                                    INSERT INTO conversations
                                    (user_id, channel, status, started_at, last_active_at, closed_at)
                                    VALUES (?, ?, ?, ?, ?, NULL)
                                    """,
                            Statement.RETURN_GENERATED_KEYS);
                    statement.setLong(1, userId);
                    statement.setString(2, CHANNEL_WECHAT);
                    statement.setString(3, STATUS_ACTIVE);
                    statement.setTimestamp(4, Timestamp.from(now));
                    statement.setTimestamp(5, Timestamp.from(now));
                    return statement;
                });
    }

    private void touchConversation(long conversationId, Instant now) {
        jdbcTemplate.update(
                "UPDATE conversations SET last_active_at = ? WHERE id = ?",
                Timestamp.from(now),
                conversationId);
    }

    private WechatConversationMemory loadMemory(long userId, long conversationId) {
        WechatConversationMemory memory = WechatConversationMemory.empty(
                properties.recentTurnLimit(),
                loadRelevantSummary(userId, conversationId));
        loadState(conversationId).ifPresent(memory::applyState);
        memory.replaceTurns(loadRecentTurns(conversationId));
        return memory;
    }

    private void closeIdleConversationsAfterSummary(Instant now) {
        List<Long> userIds = jdbcTemplate.query(
                """
                        SELECT DISTINCT user_id
                        FROM conversations
                        WHERE channel = ? AND status = ? AND last_active_at <= ?
                        """,
                (resultSet, rowNumber) -> resultSet.getLong(1),
                CHANNEL_WECHAT,
                STATUS_ACTIVE,
                Timestamp.from(now.minusSeconds(properties.sessionIdleMinutes() * 60L)));
        for (Long userId : userIds) {
            if (userId != null) {
                closeExpiredConversationsAfterSummary(userId, now);
            }
        }
    }

    private void createRollingSummaries(Instant now) {
        List<Long> conversationIds = jdbcTemplate.query(
                """
                        SELECT id
                        FROM conversations
                        WHERE channel = ? AND status = ?
                        ORDER BY id
                        """,
                (resultSet, rowNumber) -> resultSet.getLong(1),
                CHANNEL_WECHAT,
                STATUS_ACTIVE);
        for (Long conversationId : conversationIds) {
            if (conversationId != null && completedTurnsSinceLatestSummary(conversationId)
                    >= properties.rollingSummaryTurnThreshold()) {
                summarizeConversation(conversationId, now);
            }
        }
    }

    private int completedTurnsSinceLatestSummary(long conversationId) {
        long coveredMessageId = latestSummary(conversationId)
                .map(SummaryRow::coveredMessageId)
                .orElse(0L);
        List<Integer> roleCounts = jdbcTemplate.query(
                """
                        SELECT role, COUNT(*)
                        FROM conversation_messages
                        WHERE conversation_id = ? AND id > ? AND role IN ('USER', 'ASSISTANT')
                        GROUP BY role
                        """,
                (resultSet, rowNumber) -> "USER".equals(resultSet.getString(1))
                        ? resultSet.getInt(2)
                        : -resultSet.getInt(2),
                conversationId,
                coveredMessageId);
        int users = 0;
        int assistants = 0;
        for (Integer count : roleCounts) {
            if (count != null && count >= 0) {
                users = count;
            } else if (count != null) {
                assistants = -count;
            }
        }
        return Math.min(users, assistants);
    }

    private boolean summarizeConversation(long conversationId, Instant now) {
        Optional<SummaryRow> previous = latestSummary(conversationId);
        long coveredMessageId = previous.map(SummaryRow::coveredMessageId).orElse(0L);
        List<MessageRow> messages = messagesAfter(conversationId, coveredMessageId);
        if (messages.isEmpty()) {
            return true;
        }
        try {
            String summary = chatService.reply(summaryPrompt(previous.map(SummaryRow::summaryText).orElse(""), messages));
            if (summary == null || summary.isBlank()) {
                log.warn("微信会话摘要为空，conversationId={}", conversationId);
                return false;
            }
            long newCoveredMessageId = messages.get(messages.size() - 1).id();
            jdbcTemplate.update(
                    """
                            INSERT INTO conversation_summaries
                            (conversation_id, summary_text, covered_message_id, generated_at)
                            VALUES (?, ?, ?, ?)
                            """,
                    conversationId,
                    summary.strip(),
                    newCoveredMessageId,
                    Timestamp.from(now));
            return true;
        } catch (RuntimeException exception) {
            log.warn("微信会话摘要生成失败，conversationId={}, error={}",
                    conversationId, rootMessage(exception));
            return false;
        }
    }

    private void closeConversation(long conversationId, Instant now) {
        jdbcTemplate.update(
                """
                        UPDATE conversations
                        SET status = ?, closed_at = ?
                        WHERE id = ? AND status = ?
                        """,
                STATUS_CLOSED,
                Timestamp.from(now),
                conversationId,
                STATUS_ACTIVE);
    }

    private Optional<SummaryRow> latestSummary(long conversationId) {
        List<SummaryRow> values = jdbcTemplate.query(
                """
                        SELECT summary_text, COALESCE(covered_message_id, 0)
                        FROM conversation_summaries
                        WHERE conversation_id = ?
                        ORDER BY generated_at DESC, id DESC
                        LIMIT 1
                        """,
                (resultSet, rowNumber) -> new SummaryRow(resultSet.getString(1), resultSet.getLong(2)),
                conversationId);
        return values.stream().findFirst();
    }

    private String loadRelevantSummary(long userId, long conversationId) {
        Optional<SummaryRow> currentSummary = latestSummary(conversationId);
        if (currentSummary.isPresent()) {
            return currentSummary.get().summaryText();
        }
        List<String> previousSummaries = jdbcTemplate.query(
                """
                        SELECT summary.summary_text
                        FROM conversation_summaries summary
                        JOIN conversations conversation ON conversation.id = summary.conversation_id
                        WHERE conversation.user_id = ? AND conversation.id <> ?
                        ORDER BY summary.generated_at DESC, summary.id DESC
                        LIMIT 1
                        """,
                (resultSet, rowNumber) -> resultSet.getString(1),
                userId,
                conversationId);
        return previousSummaries.isEmpty() ? null : previousSummaries.get(0);
    }

    private List<MessageRow> messagesAfter(long conversationId, long coveredMessageId) {
        return jdbcTemplate.query(
                """
                        SELECT id, role, content
                        FROM conversation_messages
                        WHERE conversation_id = ? AND id > ? AND role IN ('USER', 'ASSISTANT')
                        ORDER BY id
                        """,
                (resultSet, rowNumber) -> new MessageRow(
                        resultSet.getLong(1),
                        resultSet.getString(2),
                        resultSet.getString(3)),
                conversationId,
                coveredMessageId);
    }

    private String summaryPrompt(String previousSummary, List<MessageRow> messages) {
        StringBuilder prompt = new StringBuilder("""
                你是微信助手的会话记忆压缩器。
                只根据给出的已有摘要和新增对话，输出简洁、准确的中文摘要。
                仅保留：用户已明确的事实和偏好、当前目标、已完成事项、未完成事项、待确认问题、工具调用结果。
                不要杜撰，不要解释你的工作，不要使用标题或寒暄。

                已有摘要：
                """);
        prompt.append(previousSummary == null || previousSummary.isBlank() ? "无" : previousSummary.strip())
                .append("\n\n新增对话：\n");
        for (MessageRow message : messages) {
            prompt.append("USER".equals(message.role()) ? "用户：" : "助手：")
                    .append(message.content())
                    .append('\n');
        }
        return prompt.toString();
    }

    private Optional<WechatConversationMemory.State> loadState(long conversationId) {
        List<String> values = jdbcTemplate.query(
                "SELECT state_json FROM conversation_states WHERE conversation_id = ?",
                (resultSet, rowNumber) -> resultSet.getString(1),
                conversationId);
        if (values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(values.get(0), WechatConversationMemory.State.class));
        } catch (JsonProcessingException exception) {
            log.warn("微信会话状态 JSON 解析失败，conversationId={}, error={}",
                    conversationId, rootMessage(exception));
            return Optional.empty();
        }
    }

    private List<ConversationTurn> loadRecentTurns(long conversationId) {
        List<MessageRow> newestFirst = jdbcTemplate.query(
                """
                        SELECT role, content
                        FROM conversation_messages
                        WHERE conversation_id = ? AND role IN ('USER', 'ASSISTANT')
                        ORDER BY id DESC
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new MessageRow(
                        0L,
                        resultSet.getString(1),
                        resultSet.getString(2)),
                conversationId,
                properties.recentTurnLimit() * 2);
        Collections.reverse(newestFirst);

        List<ConversationTurn> turns = new ArrayList<>();
        String pendingUser = null;
        for (MessageRow row : newestFirst) {
            if ("USER".equals(row.role())) {
                pendingUser = row.content();
            } else if ("ASSISTANT".equals(row.role()) && pendingUser != null) {
                turns.add(new ConversationTurn(pendingUser, row.content()));
                pendingUser = null;
            }
        }
        return turns;
    }

    private void insertMessage(
            long conversationId,
            String sourceMessageId,
            String role,
            String content,
            String contentType,
            Instant now) {
        jdbcTemplate.update(
                """
                        INSERT INTO conversation_messages
                        (conversation_id, source_message_id, role, content, content_type, created_at, expires_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                conversationId,
                sourceMessageId,
                textOrEmpty(role),
                textOrEmpty(content),
                textOrEmpty(contentType),
                Timestamp.from(now),
                Timestamp.from(now.plusSeconds(properties.rawRetentionDays() * 86_400L)));
    }

    private long insertAndReadKey(PreparedStatementCreator creator) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(creator, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("数据库未返回主键");
        }
        return key.longValue();
    }

    private Instant safeTime(Instant value) {
        return value == null ? Instant.now() : value;
    }

    private String safeUserId(String userId) {
        return userId == null || userId.isBlank() ? "default" : userId.strip();
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    private String truncate(String value, int maxLength) {
        String text = textOrEmpty(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record SummaryRow(String summaryText, long coveredMessageId) {
    }

    private record MessageRow(long id, String role, String content) {
    }
}
