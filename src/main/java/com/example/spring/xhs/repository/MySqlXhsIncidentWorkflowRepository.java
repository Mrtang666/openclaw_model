package com.example.spring.xhs.repository;

import com.example.spring.xhs.incident.XhsIncidentStatus;
import com.example.spring.xhs.incident.XhsIncidentTransition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class MySqlXhsIncidentWorkflowRepository implements XhsIncidentWorkflowRepository {

    private final JdbcTemplate jdbcTemplate;

    public MySqlXhsIncidentWorkflowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public XhsIncidentTransition transition(
            String projectKey,
            long incidentId,
            XhsIncidentStatus targetStatus,
            String connectionId,
            String recipientId,
            String note,
            Instant now) {
        Integer authorized = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM xhs_alert_subscriptions s
                        JOIN xhs_monitor_projects p ON p.id = s.project_id
                        WHERE p.project_key = ? AND p.status = 'ACTIVE'
                          AND s.channel = 'WECHAT' AND s.connection_id = ?
                          AND s.recipient_id = ? AND s.enabled = 1
                        """, Integer.class, projectKey, connectionId, recipientId);
        if (authorized == null || authorized == 0) {
            throw new IllegalArgumentException("当前微信连接无权处置该舆情项目");
        }

        List<IncidentRow> incidents = jdbcTemplate.query("""
                        SELECT i.id, i.status
                        FROM xhs_incidents i
                        JOIN xhs_monitor_projects p ON p.id = i.project_id
                        WHERE i.id = ? AND p.project_key = ?
                        FOR UPDATE
                        """, (rs, row) -> new IncidentRow(
                        rs.getLong("id"), XhsIncidentStatus.from(rs.getString("status"))),
                incidentId, projectKey);
        if (incidents.isEmpty()) {
            throw new IllegalArgumentException("未找到可处置的舆情事件");
        }

        XhsIncidentStatus current = incidents.get(0).status();
        if (current == targetStatus) {
            return new XhsIncidentTransition(incidentId, projectKey, current, targetStatus, false, now);
        }
        if (!current.canTransitionTo(targetStatus)) {
            throw new IllegalStateException("事件状态不能从 " + current + " 变更为 " + targetStatus);
        }

        int updated = jdbcTemplate.update("""
                        UPDATE xhs_incidents
                        SET status = ?, updated_at = ?
                        WHERE id = ? AND status = ?
                        """, targetStatus.name(), Timestamp.from(now), incidentId, current.name());
        if (updated != 1) {
            throw new IllegalStateException("事件状态已变化，请重新查询后再操作");
        }
        jdbcTemplate.update("""
                        INSERT INTO xhs_incident_actions(
                            incident_id, from_status, to_status, actor_connection_id,
                            actor_recipient_id, note, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, incidentId, current.name(), targetStatus.name(), connectionId,
                recipientId, note, Timestamp.from(now));
        return new XhsIncidentTransition(incidentId, projectKey, current, targetStatus, true, now);
    }

    private record IncidentRow(long id, XhsIncidentStatus status) {
    }
}
