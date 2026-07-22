package com.example.spring.wechat.memory.scheduler;

import com.example.spring.wechat.image.archive.ImageArchiveCleanupResult;
import com.example.spring.wechat.image.archive.ImageArchiveService;
import com.example.spring.wechat.memory.config.WechatMemoryProperties;
import com.example.spring.wechat.memory.service.WechatMemoryService;
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
    private final WechatMemoryService memoryService;
    private final ImageArchiveService imageArchiveService;
    private final WechatMemoryProperties properties;

    public WechatMemoryMaintenanceScheduler(
            JdbcTemplate jdbcTemplate,
            WechatMemoryService memoryService,
            ImageArchiveService imageArchiveService,
            WechatMemoryProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.memoryService = memoryService;
        this.imageArchiveService = imageArchiveService;
        this.properties = properties;
    }

    /**
     * 定期为闲置会话和过长会话生成摘要，减少下一轮大模型请求需要携带的原始消息量。
     */
    @Scheduled(
            fixedDelayString = "${wechat.memory.summary-maintenance-delay-ms:300000}",
            initialDelayString = "${wechat.memory.summary-maintenance-initial-delay-ms:60000}")
    public void maintainConversationSummaries() {
        maintainConversationSummaries(Instant.now());
    }

    /**
     * 暴露给测试和运维任务的摘要维护入口。
     */
    public void maintainConversationSummaries(Instant now) {
        memoryService.maintainConversationSummaries(now);
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
        ImageArchiveCleanupResult images = imageArchiveService.cleanExpiredImages(time, properties.rawRetentionDays());
        if (messages > 0 || toolLogs > 0 || images.deletedMetadata() > 0) {
            log.info("已清理过期微信记忆数据，messages={}, toolLogs={}", messages, toolLogs);
        }
    }
}
