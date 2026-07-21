package com.example.spring.wechat.memory;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.memory.scheduler.WechatMemoryMaintenanceScheduler;
import com.example.spring.wechat.memory.service.MySqlWechatMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(
        args = "/status",
        properties = "wechat.memory.rolling-summary-turn-threshold=2")
class WechatMemoryMaintenanceSchedulerTests {

    @Autowired
    private MySqlWechatMemoryService memoryService;

    @Autowired
    private WechatMemoryMaintenanceScheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ChatService chatService;

    @BeforeEach
    void clearMemoryTables() {
        jdbcTemplate.update("DELETE FROM tool_execution_logs");
        jdbcTemplate.update("DELETE FROM conversation_summaries");
        jdbcTemplate.update("DELETE FROM conversation_messages");
        jdbcTemplate.update("DELETE FROM conversation_states");
        jdbcTemplate.update("DELETE FROM user_preferences");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void removesExpiredRawMessagesAndToolLogs() {
        Instant now = Instant.parse("2026-07-21T01:00:00Z");
        memoryService.acceptIncoming("wx-user", "expired-message", "过期消息", "TEXT", now);
        memoryService.recordToolExecution("wx-user", "weather", java.util.Map.of(), "过期日志", "SUCCESS", now);
        memoryService.acceptIncoming("wx-user", "active-message", "保留消息", "TEXT", now.plusSeconds(1));

        jdbcTemplate.update(
                "UPDATE conversation_messages SET expires_at = ? WHERE source_message_id = ?",
                java.sql.Timestamp.from(now.minusSeconds(1)),
                "expired-message");
        jdbcTemplate.update(
                "UPDATE tool_execution_logs SET expires_at = ?",
                java.sql.Timestamp.from(now.minusSeconds(1)));

        scheduler.cleanExpiredRawData(now);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversation_messages WHERE source_message_id = 'expired-message'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversation_messages WHERE source_message_id = 'active-message'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tool_execution_logs",
                Integer.class)).isZero();
    }

    @Test
    void summarizesInactiveConversationBeforeClosingIt() {
        Instant firstMessageAt = Instant.parse("2026-07-21T01:00:00Z");
        Instant maintenanceAt = firstMessageAt.plusSeconds(61 * 60L);
        when(chatService.reply(anyString())).thenReturn("用户计划周末去杭州，偏好简洁建议。");

        memoryService.acceptIncoming("summary-user", "message-1", "我周末去杭州", "TEXT", firstMessageAt);
        memoryService.recordAssistantMessage("summary-user", "好的，我会记住。", "TEXT", firstMessageAt);

        scheduler.maintainConversationSummaries(maintenanceAt);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM conversations c JOIN users u ON u.id = c.user_id WHERE u.wechat_user_id = 'summary-user'",
                String.class)).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT summary_text FROM conversation_summaries",
                String.class)).isEqualTo("用户计划周末去杭州，偏好简洁建议。");
    }

    @Test
    void rollingSummaryIsAvailableWhenUserStartsNextConversation() {
        Instant firstMessageAt = Instant.parse("2026-07-21T01:00:00Z");
        when(chatService.reply(anyString())).thenReturn("用户正在准备杭州旅行，并希望获得简洁建议。");

        for (int index = 1; index <= 2; index++) {
            memoryService.acceptIncoming(
                    "rolling-user",
                    "message-" + index,
                    "travel question " + index,
                    "TEXT",
                    firstMessageAt.plusSeconds(index));
            memoryService.recordAssistantMessage(
                    "rolling-user",
                    "travel answer " + index,
                    "TEXT",
                    firstMessageAt.plusSeconds(index));
        }

        scheduler.maintainConversationSummaries(firstMessageAt.plusSeconds(10));
        memoryService.acceptIncoming(
                "rolling-user",
                "message-next",
                "明天呢",
                "TEXT",
                firstMessageAt.plusSeconds(61 * 60L));

        assertThat(memoryService.memoryFor("rolling-user").conversationSummary())
                .hasValueSatisfying(summary -> assertThat(summary).contains("杭州旅行"));
    }

    @Test
    void rollingSummaryDoesNotCoverMessageWrittenWhileSummaryIsGenerating() {
        Instant firstMessageAt = Instant.parse("2026-07-21T01:00:00Z");
        Instant maintenanceAt = firstMessageAt.plusSeconds(10);
        for (int index = 1; index <= 2; index++) {
            memoryService.acceptIncoming(
                    "concurrent-user",
                    "message-" + index,
                    "question " + index,
                    "TEXT",
                    firstMessageAt.plusSeconds(index));
            memoryService.recordAssistantMessage(
                    "concurrent-user",
                    "answer " + index,
                    "TEXT",
                    firstMessageAt.plusSeconds(index));
        }
        Long conversationId = jdbcTemplate.queryForObject(
                "SELECT c.id FROM conversations c JOIN users u ON u.id = c.user_id WHERE u.wechat_user_id = 'concurrent-user'",
                Long.class);
        doAnswer(invocation -> {
            jdbcTemplate.update(
                    """
                            INSERT INTO conversation_messages
                            (conversation_id, source_message_id, role, content, content_type, created_at, expires_at)
                            VALUES (?, 'message-late', 'USER', 'late question', 'TEXT', ?, ?)
                            """,
                    conversationId,
                    java.sql.Timestamp.from(maintenanceAt),
                    java.sql.Timestamp.from(maintenanceAt.plusSeconds(30L * 86_400L)));
            return "已压缩前两轮对话。";
        }).when(chatService).reply(anyString());

        scheduler.maintainConversationSummaries(maintenanceAt);

        Long coveredMessageId = jdbcTemplate.queryForObject(
                "SELECT covered_message_id FROM conversation_summaries",
                Long.class);
        Long lateMessageId = jdbcTemplate.queryForObject(
                "SELECT id FROM conversation_messages WHERE source_message_id = 'message-late'",
                Long.class);
        assertThat(coveredMessageId).isLessThan(lateMessageId);
    }
}
