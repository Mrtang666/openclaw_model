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
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(
        args = "/status",
        properties = "wechat.memory.rolling-summary-turn-threshold=2")
@ActiveProfiles("test")
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
        TestDatabaseGuard.assertUsingTestDatabase(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM wechat_images");
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
    void removesExpiredImageMetadataAndLocalFile() throws Exception {
        Instant now = Instant.parse("2026-07-21T01:00:00Z");
        Path localFile = Files.createTempFile("expired-wechat-image", ".png");
        Files.writeString(localFile, "old image");
        jdbcTemplate.update(
                """
                        INSERT INTO wechat_images
                        (wechat_user_id, message_id, source_type, source_reference, image_index,
                         file_name, mime_type, sha256, md5, size_bytes, local_path, image_url,
                         prompt, description, status, created_at, used_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "wx-user",
                "image-message",
                "USER_UPLOAD",
                "wechat://image",
                1,
                "old.png",
                "image/png",
                "sha-old",
                "md5-old",
                9,
                localFile.toString(),
                "",
                "",
                "",
                "USED",
                java.sql.Timestamp.from(now.minusSeconds(31L * 86_400L)),
                null);

        scheduler.cleanExpiredRawData(now);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wechat_images", Integer.class)).isZero();
        assertThat(Files.exists(localFile)).isFalse();
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
