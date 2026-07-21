package com.example.spring.wechat.memory;

import com.example.spring.wechat.memory.scheduler.WechatMemoryMaintenanceScheduler;
import com.example.spring.wechat.memory.service.MySqlWechatMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "/status")
class WechatMemoryMaintenanceSchedulerTests {

    @Autowired
    private MySqlWechatMemoryService memoryService;

    @Autowired
    private WechatMemoryMaintenanceScheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
}
