package com.example.spring.wechat.care.repository;

import com.example.spring.wechat.care.model.CareActor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class CareAuditRepository {

    private final JdbcTemplate jdbc;

    public CareAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(
            CareActor actor,
            Long patientUserId,
            String action,
            String permission,
            String resourceType,
            String resourceId,
            String result,
            String reason,
            String requestId,
            Instant now) {
        jdbc.update("""
                INSERT INTO medical_access_audit_logs
                (actor_user_id,actor_role,target_patient_user_id,action,permission_code,resource_type,
                 resource_id,result,reason,request_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, actor == null ? null : actor.userId(), actor == null ? null : actor.role().name(),
                patientUserId, action, permission, resourceType, resourceId, result,
                limit(reason, 1000), requestId, Timestamp.from(now));
    }

    private static String limit(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max);
    }
}
