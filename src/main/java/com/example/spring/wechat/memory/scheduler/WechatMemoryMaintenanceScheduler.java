package com.example.spring.wechat.memory.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * 定时清理已过保留期的微信原始消息与工具执行日志。
 */
@Component
public class WechatMemoryMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(WechatMemoryMaintenanceScheduler.class);

    private final JdbcTemplate jdbcTemplate;

    public WechatMemoryMaintenanceScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanExpiredRawData() {
        cleanExpiredRawData(Instant.now());
    }

    /**
     * 公开给测试和运维任务调用的清理入口，不删除会话摘要或长期偏好。
     */
    public void cleanExpiredRawData(Instant now) {
        Instant time = now == null ? Instant.now() : now;
        int messages = jdbcTemplate.update(
                "DELETE FROM conversation_messages WHERE expires_at < ?",
                Timestamp.from(time));
        int toolLogs = jdbcTemplate.update(
                "DELETE FROM tool_execution_logs WHERE expires_at < ?",
                Timestamp.from(time));
        if (messages > 0 || toolLogs > 0) {
            log.info("已清理过期微信记忆数据，messages={}, toolLogs={}", messages, toolLogs);
        }
    }
}
