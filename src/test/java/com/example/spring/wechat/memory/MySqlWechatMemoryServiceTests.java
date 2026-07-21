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
}
