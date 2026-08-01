package com.example.spring.xhs.repository;

import com.example.spring.xhs.alert.XhsAlertDelivery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class MySqlXhsAlertRepository implements XhsAlertRepository {

    private final JdbcTemplate jdbcTemplate;

    public MySqlXhsAlertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public long subscribeWechat(String projectKey, String connectionId, String recipientId,
                                int minimumRiskScore, int cooldownMinutes, Instant now) {
        long projectId = projectId(projectKey);
        int threshold = Math.max(0, Math.min(100, minimumRiskScore));
        int cooldown = Math.max(1, cooldownMinutes);
        String normalizedConnectionId = required(connectionId, "connectionId");
        String normalizedRecipientId = required(recipientId, "recipientId");
        String ruleName = "wechat-risk-" + threshold + "-cooldown-" + cooldown;
        Timestamp time = Timestamp.from(now);
        jdbcTemplate.update("""
                        INSERT INTO xhs_alert_rules(
                            project_id, name, minimum_risk_score, cooldown_minutes, enabled, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 1, ?, ?)
                        ON DUPLICATE KEY UPDATE enabled = 1, updated_at = VALUES(updated_at)
                        """, projectId, ruleName, threshold, cooldown, time, time);
        long ruleId = jdbcTemplate.queryForObject(
                "SELECT id FROM xhs_alert_rules WHERE project_id = ? AND name = ?",
                Long.class, projectId, ruleName);
        jdbcTemplate.update("""
                        UPDATE xhs_alert_subscriptions
                        SET enabled = 0, updated_at = ?
                        WHERE project_id = ? AND channel = 'WECHAT'
                          AND connection_id = ? AND recipient_id = ?
                        """, time, projectId, normalizedConnectionId, normalizedRecipientId);
        jdbcTemplate.update("""
                        INSERT INTO xhs_alert_subscriptions(
                            project_id, rule_id, channel, connection_id, recipient_id, enabled, created_at, updated_at)
                        VALUES (?, ?, 'WECHAT', ?, ?, 1, ?, ?)
                        ON DUPLICATE KEY UPDATE enabled = 1, updated_at = VALUES(updated_at)
                        """, projectId, ruleId, normalizedConnectionId,
                normalizedRecipientId, time, time);
        return jdbcTemplate.queryForObject("""
                        SELECT id FROM xhs_alert_subscriptions
                        WHERE project_id = ? AND rule_id = ? AND channel = 'WECHAT'
                          AND connection_id = ? AND recipient_id = ?
                        """, Long.class, projectId, ruleId, normalizedConnectionId, normalizedRecipientId);
    }

    @Override
    @Transactional
    public int createEventsForIncident(long incidentId, int riskScore, String riskLevel, Instant now) {
        Timestamp time = Timestamp.from(now);
        int created = jdbcTemplate.update("""
                        INSERT IGNORE INTO xhs_alert_events(
                            alert_key, rule_id, incident_id, status, risk_score, created_at)
                        SELECT CONCAT(r.id, ':', i.id, ':', ?, ':',
                                      FLOOR(UNIX_TIMESTAMP(?) / (r.cooldown_minutes * 60))),
                               r.id, i.id, 'PENDING', ?, ?
                        FROM xhs_incidents i
                        JOIN xhs_alert_rules r ON r.project_id = i.project_id
                        WHERE i.id = ? AND r.enabled = 1 AND r.minimum_risk_score <= ?
                          AND EXISTS (
                              SELECT 1 FROM xhs_alert_subscriptions s
                              WHERE s.rule_id = r.id AND s.enabled = 1)
                        """, riskLevel, time, riskScore, time, incidentId, riskScore);
        jdbcTemplate.update("""
                        INSERT IGNORE INTO xhs_alert_deliveries(
                            alert_event_id, subscription_id, status, attempt_count, updated_at)
                        SELECT e.id, s.id, 'PENDING', 0, ?
                        FROM xhs_alert_events e
                        JOIN xhs_alert_rules r ON r.id = e.rule_id
                        JOIN xhs_alert_subscriptions s ON s.rule_id = e.rule_id
                        WHERE e.incident_id = ? AND s.enabled = 1
                          AND e.alert_key = CONCAT(r.id, ':', e.incident_id, ':', ?, ':',
                              FLOOR(UNIX_TIMESTAMP(?) / (r.cooldown_minutes * 60)))
                        """, time, incidentId, riskLevel, time);
        return created;
    }

    @Override
    public List<XhsAlertDelivery> findPendingDeliveries(int maxAttempts, int limit) {
        return jdbcTemplate.query("""
                        SELECT d.id delivery_id, e.id alert_event_id, p.project_key,
                               s.connection_id, s.recipient_id, i.title, i.risk_category,
                               e.risk_score, i.risk_level, i.post_count, d.attempt_count
                        FROM xhs_alert_deliveries d
                        JOIN xhs_alert_events e ON e.id = d.alert_event_id
                        JOIN xhs_incidents i ON i.id = e.incident_id
                        JOIN xhs_monitor_projects p ON p.id = i.project_id
                        JOIN xhs_alert_subscriptions s ON s.id = d.subscription_id
                        WHERE d.status IN ('PENDING', 'FAILED') AND d.attempt_count < ?
                          AND e.status = 'PENDING'
                        ORDER BY d.updated_at, d.id
                        LIMIT ?
                        """,
                (rs, row) -> new XhsAlertDelivery(
                        rs.getLong("delivery_id"), rs.getLong("alert_event_id"),
                        rs.getString("project_key"), rs.getString("connection_id"),
                        rs.getString("recipient_id"), rs.getString("title"),
                        rs.getString("risk_category"), rs.getInt("risk_score"),
                        rs.getString("risk_level"), rs.getInt("post_count"),
                        rs.getInt("attempt_count")),
                Math.max(1, maxAttempts), Math.max(1, limit));
    }

    @Override
    @Transactional
    public void markDeliverySent(long deliveryId, long alertEventId, int maxAttempts, Instant now) {
        Timestamp time = Timestamp.from(now);
        jdbcTemplate.update("""
                        UPDATE xhs_alert_deliveries
                        SET status = 'SENT', attempt_count = attempt_count + 1,
                            last_error = NULL, sent_at = ?, updated_at = ?
                        WHERE id = ?
                        """, time, time, deliveryId);
        reconcileEvent(alertEventId, maxAttempts, time);
    }

    @Override
    @Transactional
    public void markDeliveryFailed(
            long deliveryId,
            long alertEventId,
            String errorMessage,
            int maxAttempts,
            Instant now) {
        jdbcTemplate.update("""
                        UPDATE xhs_alert_deliveries
                        SET status = 'FAILED', attempt_count = attempt_count + 1,
                            last_error = ?, updated_at = ?
                        WHERE id = ?
                """, truncate(errorMessage, 1000), Timestamp.from(now), deliveryId);
        reconcileEvent(alertEventId, maxAttempts, Timestamp.from(now));
    }

    @Override
    public boolean acknowledge(String projectKey, long alertEventId, String connectionId,
                               String recipientId, Instant now) {
        int updated = jdbcTemplate.update("""
                        UPDATE xhs_alert_events e
                        JOIN xhs_incidents i ON i.id = e.incident_id
                        JOIN xhs_monitor_projects p ON p.id = i.project_id
                        SET e.status = 'ACKNOWLEDGED', e.acknowledged_at = ?
                        WHERE e.id = ? AND p.project_key = ? AND EXISTS (
                            SELECT 1 FROM xhs_alert_subscriptions s
                            JOIN xhs_alert_deliveries d ON d.subscription_id = s.id
                            WHERE s.rule_id = e.rule_id AND s.connection_id = ?
                              AND s.recipient_id = ?
                              AND d.alert_event_id = e.id AND d.status = 'SENT')
                        """, Timestamp.from(now), alertEventId, required(projectKey, "projectKey"),
                required(connectionId, "connectionId"), required(recipientId, "recipientId"));
        return updated > 0;
    }

    private void reconcileEvent(long alertEventId, int maxAttempts, Timestamp now) {
        jdbcTemplate.update("""
                        UPDATE xhs_alert_events e
                        SET e.status = 'SENT', e.sent_at = ?
                        WHERE e.id = ? AND NOT EXISTS (
                            SELECT 1 FROM xhs_alert_deliveries d
                            WHERE d.alert_event_id = e.id AND d.status <> 'SENT')
                        """, now, alertEventId);
        jdbcTemplate.update("""
                        UPDATE xhs_alert_events e
                        SET e.status = 'FAILED'
                        WHERE e.id = ? AND NOT EXISTS (
                            SELECT 1 FROM xhs_alert_deliveries d
                            WHERE d.alert_event_id = e.id
                              AND d.status <> 'SENT' AND d.attempt_count < ?)
                          AND EXISTS (
                            SELECT 1 FROM xhs_alert_deliveries d
                            WHERE d.alert_event_id = e.id AND d.status = 'FAILED')
                        """, alertEventId, Math.max(1, maxAttempts));
    }

    private long projectId(String projectKey) {
        List<Long> values = jdbcTemplate.query(
                "SELECT id FROM xhs_monitor_projects WHERE project_key = ? AND status = 'ACTIVE'",
                (rs, row) -> rs.getLong(1), required(projectKey, "projectKey"));
        if (values.isEmpty()) {
            throw new IllegalArgumentException("未找到启用的小红书舆情项目：" + projectKey);
        }
        return values.get(0);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.strip();
    }

    private String truncate(String value, int maxLength) {
        String text = value == null ? "" : value.strip();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
