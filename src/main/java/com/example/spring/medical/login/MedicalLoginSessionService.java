package com.example.spring.medical.login;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

@Service
public class MedicalLoginSessionService {

    private static final Logger log = LoggerFactory.getLogger(MedicalLoginSessionService.class);

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public MedicalLoginSessionService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public void recordWaiting(String connectionId, String loginSessionId, String requestedRole, Instant createdAt) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null || isBlank(connectionId) || isBlank(loginSessionId) || isBlank(requestedRole)) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO medical_login_sessions(
                        connection_id,
                        login_session_id,
                        requested_role,
                        status,
                        bound_user_id,
                        created_at,
                        bound_at
                    ) VALUES (?, ?, ?, 'WAITING', NULL, ?, NULL)
                    ON DUPLICATE KEY UPDATE
                        connection_id = VALUES(connection_id),
                        requested_role = VALUES(requested_role),
                        status = 'WAITING',
                        bound_user_id = NULL,
                        bound_at = NULL
                    """,
                    connectionId,
                    loginSessionId,
                    requestedRole,
                    Timestamp.from(createdAt == null ? Instant.now() : createdAt));
        } catch (DataAccessException exception) {
            log.warn("记录医疗身份登录会话失败，不影响微信扫码登录，loginSessionId={}, error={}",
                    loginSessionId, rootMessage(exception));
        }
    }

    public void markBound(String loginSessionId, Instant boundAt) {
        updateStatus(loginSessionId, "BOUND", boundAt);
    }

    public void markExpired(String loginSessionId) {
        updateStatus(loginSessionId, "EXPIRED", null);
    }

    public void markCancelled(String loginSessionId) {
        updateStatus(loginSessionId, "CANCELLED", null);
    }

    private void updateStatus(String loginSessionId, String status, Instant boundAt) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null || isBlank(loginSessionId)) {
            return;
        }
        try {
            if (boundAt == null) {
                jdbcTemplate.update("""
                        UPDATE medical_login_sessions
                        SET status = ?
                        WHERE login_session_id = ?
                        """, status, loginSessionId);
            } else {
                jdbcTemplate.update("""
                        UPDATE medical_login_sessions
                        SET status = ?, bound_at = ?
                        WHERE login_session_id = ?
                        """, status, Timestamp.from(boundAt), loginSessionId);
            }
        } catch (DataAccessException exception) {
            log.warn("更新医疗身份登录会话失败，不影响微信扫码登录，loginSessionId={}, status={}, error={}",
                    loginSessionId, status, rootMessage(exception));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
