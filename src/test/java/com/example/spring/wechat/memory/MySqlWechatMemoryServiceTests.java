package com.example.spring.wechat.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
class MySqlWechatMemoryServiceTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearMemoryTables() {
        TestDatabaseGuard.assertUsingTestDatabase(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM tool_execution_logs");
        jdbcTemplate.update("DELETE FROM conversation_summaries");
        jdbcTemplate.update("DELETE FROM conversation_messages");
        jdbcTemplate.update("DELETE FROM conversation_states");
        jdbcTemplate.update("DELETE FROM user_preferences");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void opensNewConversationAfterSixtyMinutesOfInactivity() throws Exception {
        Object service = memoryService();
        Method open = service.getClass().getMethod("open", String.class, Instant.class);
        Instant first = Instant.parse("2026-07-21T01:00:00Z");

        Object firstSession = open.invoke(service, "wx-user", first);
        Object sameSession = open.invoke(service, "wx-user", first.plus(Duration.ofMinutes(59)));
        Object newSession = open.invoke(service, "wx-user", first.plus(Duration.ofMinutes(120)));
        Method conversationId = firstSession.getClass().getMethod("conversationId");

        assertThat(conversationId.invoke(sameSession)).isEqualTo(conversationId.invoke(firstSession));
        assertThat(conversationId.invoke(newSession)).isNotEqualTo(conversationId.invoke(firstSession));
    }

    @Test
    void acceptsOneWechatMessageIdOnlyOnce() throws Exception {
        Object service = memoryService();
        Method acceptIncoming = service.getClass().getMethod(
                "acceptIncoming",
                String.class,
                String.class,
                String.class,
                String.class,
                Instant.class);
        Instant now = Instant.parse("2026-07-21T01:00:00Z");

        assertThat(acceptIncoming.invoke(service, "wx-user", "msg-1", "你好", "TEXT", now))
                .isEqualTo(true);
        assertThat(acceptIncoming.invoke(service, "wx-user", "msg-1", "你好", "TEXT", now))
                .isEqualTo(false);
    }

    @Test
    void restoresCompletedTurnFromPersistedMessages() throws Exception {
        Object service = memoryService();
        Method acceptIncoming = service.getClass().getMethod(
                "acceptIncoming",
                String.class,
                String.class,
                String.class,
                String.class,
                Instant.class);
        Method recordAssistantMessage = service.getClass().getMethod(
                "recordAssistantMessage",
                String.class,
                String.class,
                String.class,
                Instant.class);
        Method open = service.getClass().getMethod("open", String.class, Instant.class);
        Instant now = Instant.parse("2026-07-21T01:00:00Z");

        acceptIncoming.invoke(service, "wx-user", "msg-1", "我在杭州", "TEXT", now);
        recordAssistantMessage.invoke(service, "wx-user", "记住了，你在杭州。", "TEXT", now);

        Object session = open.invoke(service, "wx-user", now);
        Object memory = session.getClass().getMethod("memory").invoke(session);
        Object turns = memory.getClass().getMethod("snapshot").invoke(memory);

        assertThat(turns).hasToString("[ConversationTurn[userText=我在杭州, assistantText=记住了，你在杭州。]]");
    }

    @Test
    void startNewConversationClosesActiveConversationAndNextMessageCreatesAnother() throws Exception {
        Object service = memoryService();
        Method acceptIncoming = service.getClass().getMethod(
                "acceptIncoming",
                String.class,
                String.class,
                String.class,
                String.class,
                Instant.class);
        Method recordAssistantMessage = service.getClass().getMethod(
                "recordAssistantMessage",
                String.class,
                String.class,
                String.class,
                Instant.class);
        Method startNewConversation = service.getClass().getMethod(
                "startNewConversation",
                String.class,
                Instant.class);
        Instant first = Instant.parse("2026-07-27T00:00:00Z");

        assertThat(acceptIncoming.invoke(service, "wx-new", "msg-1", "hello", "TEXT", first))
                .isEqualTo(true);
        recordAssistantMessage.invoke(service, "wx-new", "hi", "TEXT", first.plusSeconds(1));
        Long firstConversationId = activeConversationId("wx-new");

        startNewConversation.invoke(service, "wx-new", first.plusSeconds(2));

        assertThat(activeConversationId("wx-new")).isNull();
        assertThat(conversationStatus(firstConversationId)).isEqualTo("CLOSED");
        assertThat(messageContents()).doesNotContain("#new");

        assertThat(acceptIncoming.invoke(service, "wx-new", "msg-2", "fresh topic", "TEXT", first.plusSeconds(3)))
                .isEqualTo(true);
        Long secondConversationId = activeConversationId("wx-new");

        assertThat(secondConversationId).isNotNull();
        assertThat(secondConversationId).isNotEqualTo(firstConversationId);
    }

    private Object memoryService() throws Exception {
        try {
            Class<?> serviceType = Class.forName(
                    "com.example.spring.wechat.memory.service.MySqlWechatMemoryService");
            return applicationContext.getBean(serviceType);
        } catch (ClassNotFoundException exception) {
            fail("MySQL 微信记忆服务尚未实现");
            return null;
        }
    }

    private Long activeConversationId(String wechatUserId) {
        List<Long> ids = jdbcTemplate.query(
                """
                        SELECT c.id
                        FROM conversations c
                        JOIN users u ON u.id = c.user_id
                        WHERE u.wechat_user_id = ? AND c.status = 'ACTIVE'
                        ORDER BY c.id
                        """,
                (resultSet, rowNumber) -> resultSet.getLong(1),
                wechatUserId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String conversationStatus(Long conversationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM conversations WHERE id = ?",
                String.class,
                conversationId);
    }

    private List<String> messageContents() {
        return jdbcTemplate.query(
                "SELECT content FROM conversation_messages ORDER BY id",
                (resultSet, rowNumber) -> resultSet.getString(1));
    }
}
