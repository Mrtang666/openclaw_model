package com.example.spring.xhs.schedule;

import com.example.spring.wechat.bot.WechatBotService;
import com.example.spring.wechat.email.client.EmailClient;
import com.example.spring.wechat.email.config.EmailProperties;
import com.example.spring.wechat.email.model.EmailAttachment;
import com.example.spring.wechat.email.model.EmailMessage;
import com.example.spring.xhs.config.XhsScheduledReportProperties;
import com.example.spring.xhs.report.XhsReportArtifactStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class XhsScheduledReportDeliveryService {

    private final JdbcTemplate jdbcTemplate;
    private final EmailClient emailClient;
    private final EmailProperties emailProperties;
    private final WechatBotService wechatBotService;
    private final XhsReportArtifactStorage storage;
    private final XhsScheduledReportProperties properties;
    private final Duration claimTimeout;

    public XhsScheduledReportDeliveryService(
            JdbcTemplate jdbcTemplate, EmailClient emailClient, EmailProperties emailProperties,
            WechatBotService wechatBotService, XhsReportArtifactStorage storage,
            XhsScheduledReportProperties properties,
            @Value("${xhs.scheduled-report.delivery-claim-timeout:5m}") Duration claimTimeout) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailClient = emailClient;
        this.emailProperties = emailProperties;
        this.wechatBotService = wechatBotService;
        this.storage = storage;
        this.properties = properties;
        this.claimTimeout = claimTimeout == null || claimTimeout.isNegative() || claimTimeout.isZero()
                ? Duration.ofMinutes(5) : claimTimeout;
    }

    public int dispatchPending() {
        String claimToken = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Timestamp staleBefore = Timestamp.from(now.minus(claimTimeout));
        jdbcTemplate.update("""
                UPDATE xhs_report_deliveries
                SET status = 'PENDING', claim_token = NULL, claimed_at = NULL, updated_at = ?
                WHERE status = 'PROCESSING' AND claimed_at < ?
                """, Timestamp.from(now), staleBefore);
        jdbcTemplate.update("""
                UPDATE xhs_report_deliveries
                SET status = 'PROCESSING', claim_token = ?, claimed_at = ?, updated_at = ?
                WHERE id IN (
                    SELECT id FROM (
                        SELECT id FROM xhs_report_deliveries
                        WHERE status = 'PENDING' AND next_attempt_at <= ?
                          AND attempt_count < ?
                        ORDER BY next_attempt_at, id LIMIT 20
                    ) claimable
                )
                  AND status = 'PENDING'
                """, claimToken, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now),
                properties.maxDeliveryAttempts());
        List<Delivery> deliveries = jdbcTemplate.query("""
                SELECT d.id, d.run_id, d.attempt_count, r.channel,
                       r.connection_id, r.target_value, s.name schedule_name,
                       p.name project_name, p.project_key
                FROM xhs_report_deliveries d
                JOIN xhs_report_recipients r ON r.id = d.recipient_id
                JOIN xhs_report_runs rr ON rr.id = d.run_id
                JOIN xhs_report_schedules s ON s.id = rr.schedule_id
                JOIN xhs_monitor_projects p ON p.id = s.project_id
                WHERE d.status = 'PROCESSING' AND d.claim_token = ?
                ORDER BY d.next_attempt_at, d.id LIMIT 20
                """, this::mapDelivery, claimToken);
        deliveries.forEach(delivery -> dispatch(delivery, claimToken));
        return deliveries.size();
    }

    @Transactional
    public void retry(long deliveryId) {
        List<Long> runIds = jdbcTemplate.query(
                "SELECT run_id FROM xhs_report_deliveries WHERE id = ? AND status = 'FAILED'",
                (rs, row) -> rs.getLong("run_id"), deliveryId);
        if (runIds.isEmpty()) {
            throw new IllegalArgumentException("仅失败的报告投递可以重试");
        }
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE xhs_report_deliveries
                SET status = 'PENDING', attempt_count = 0, next_attempt_at = ?, last_error = NULL, updated_at = ?
                WHERE id = ? AND status = 'FAILED'
                """, Timestamp.from(now), Timestamp.from(now), deliveryId);
        if (updated != 1) {
            throw new IllegalArgumentException("仅失败的报告投递可以重试");
        }
        jdbcTemplate.update("""
                UPDATE xhs_report_runs
                SET status = 'DELIVERING', finished_at = NULL, error_message = NULL, updated_at = ?
                WHERE id = ?
                """, Timestamp.from(now), runIds.get(0));
    }

    private void dispatch(Delivery delivery, String claimToken) {
        try {
            List<Artifact> artifacts = artifacts(delivery.runId());
            if (artifacts.isEmpty()) {
                throw new IllegalStateException("报告没有可投递文件");
            }
            if ("EMAIL".equals(delivery.channel())) {
                sendEmail(delivery, artifacts);
            } else if ("WECHAT".equals(delivery.channel())) {
                sendWechat(delivery, artifacts);
            } else {
                throw new IllegalStateException("不支持的报告投递渠道：" + delivery.channel());
            }
            Instant now = Instant.now();
            jdbcTemplate.update("""
                    UPDATE xhs_report_deliveries
                    SET status = 'SENT', attempt_count = attempt_count + 1, sent_at = ?, last_error = NULL, updated_at = ?
                    WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?
                    """, Timestamp.from(now), Timestamp.from(now), delivery.id(), claimToken);
        } catch (RuntimeException exception) {
            recordFailure(delivery, exception, claimToken);
        }
        refreshRun(delivery.runId());
        jdbcTemplate.update("""
                UPDATE xhs_report_deliveries SET status = 'PENDING', claim_token = NULL, claimed_at = NULL
                WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?
                """, delivery.id(), claimToken);
    }

    private void sendEmail(Delivery delivery, List<Artifact> artifacts) {
        if (!emailProperties.enabled()) {
            throw new IllegalStateException("邮件功能未启用");
        }
        String fileList = artifacts.stream().map(value -> "- " + value.fileName())
                .collect(java.util.stream.Collectors.joining("\n"));
        EmailMessage message = new EmailMessage(
                List.of(delivery.target()),
                "[小红书舆情] " + delivery.projectName() + " 定时报告",
                "项目：" + delivery.projectName() + "（" + delivery.projectKey() + "）\n"
                        + "报告计划：" + delivery.scheduleName() + "\n"
                        + "附件文件：\n" + fileList + "\n\n"
                        + "本报告仅覆盖已采集并完成分析的数据。",
                List.of(), List.of());
        List<EmailAttachment> attachments = artifacts.stream()
                .map(value -> new EmailAttachment(storage.read(value.storageKey()), value.fileName(), value.contentType()))
                .toList();
        emailClient.sendWithAttachments(message, attachments);
    }

    private void sendWechat(Delivery delivery, List<Artifact> artifacts) {
        String summary = "小红书舆情定时报告已生成\n项目：" + delivery.projectName()
                + "\n计划：" + delivery.scheduleName()
                + "\n文件：" + artifacts.stream().map(Artifact::fileName)
                .collect(java.util.stream.Collectors.joining("、"));
        if (!wechatBotService.sendProactiveText(delivery.connectionId(), delivery.target(), summary)) {
            throw new IllegalStateException("目标微信连接不可用");
        }
        for (Artifact artifact : artifacts) {
            if (!wechatBotService.sendProactiveFile(
                    delivery.connectionId(), delivery.target(), storage.read(artifact.storageKey()),
                    artifact.fileName(), "小红书舆情定时报告")) {
                throw new IllegalStateException("微信报告文件发送失败：" + artifact.fileName());
            }
        }
    }

    private void recordFailure(Delivery delivery, Throwable throwable, String claimToken) {
        int attempt = delivery.attemptCount() + 1;
        boolean terminal = attempt >= properties.maxDeliveryAttempts();
        Instant now = Instant.now();
        Instant next = now.plusSeconds(Math.min(3600L, 60L * attempt * attempt));
        jdbcTemplate.update("""
                UPDATE xhs_report_deliveries
                SET status = ?, attempt_count = ?, next_attempt_at = ?, last_error = ?, updated_at = ?
                WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?
                """, terminal ? "FAILED" : "PENDING", attempt, Timestamp.from(next),
                safeMessage(throwable), Timestamp.from(now), delivery.id(), claimToken);
    }

    private void refreshRun(long runId) {
        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM xhs_report_deliveries WHERE run_id = ? AND status IN ('PENDING', 'PROCESSING')",
                Integer.class, runId);
        if (pending != null && pending > 0) {
            return;
        }
        Integer failed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM xhs_report_deliveries WHERE run_id = ? AND status = 'FAILED'",
                Integer.class, runId);
        String partial = jdbcTemplate.queryForObject(
                "SELECT COALESCE(partial_reason, '') FROM xhs_report_runs WHERE id = ?", String.class, runId);
        String status = (failed != null && failed > 0) || (partial != null && !partial.isBlank())
                ? "PARTIAL" : "SUCCEEDED";
        Instant now = Instant.now();
        jdbcTemplate.update("UPDATE xhs_report_runs SET status = ?, finished_at = ?, updated_at = ? WHERE id = ?",
                status, Timestamp.from(now), Timestamp.from(now), runId);
    }

    private List<Artifact> artifacts(long runId) {
        return jdbcTemplate.query("""
                SELECT storage_key, file_name, content_type FROM xhs_report_artifacts
                WHERE run_id = ? ORDER BY id
                """, (rs, row) -> new Artifact(rs.getString("storage_key"), rs.getString("file_name"),
                rs.getString("content_type")), runId);
    }

    private Delivery mapDelivery(ResultSet rs, int row) throws SQLException {
        return new Delivery(rs.getLong("id"), rs.getLong("run_id"), rs.getInt("attempt_count"),
                rs.getString("channel"), rs.getString("connection_id"), rs.getString("target_value"),
                rs.getString("schedule_name"), rs.getString("project_name"), rs.getString("project_key"));
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? "" : throwable.getMessage();
        String value = message == null || message.isBlank() ? "未知投递错误" : message;
        return value.substring(0, Math.min(value.length(), 2000));
    }

    private record Delivery(long id, long runId, int attemptCount, String channel, String connectionId,
                            String target, String scheduleName, String projectName, String projectKey) {
    }

    private record Artifact(String storageKey, String fileName, String contentType) {
    }
}
