package com.example.spring.medical.login;

import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.MedicalUser;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class MedicalLoginSessionService {

    private static final Logger log = LoggerFactory.getLogger(MedicalLoginSessionService.class);

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ObjectProvider<MedicalIdentityRepository> identityRepositoryProvider;
    private final Clock clock;

    public MedicalLoginSessionService(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<MedicalIdentityRepository> identityRepositoryProvider,
            ObjectProvider<Clock> clockProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.identityRepositoryProvider = identityRepositoryProvider;
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
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

    public void bindWechatUser(
            String loginSessionId,
            String connectionId,
            String fromUserId,
            String displayName,
            Instant boundAt) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        MedicalIdentityRepository identityRepository = identityRepositoryProvider.getIfAvailable();
        if (jdbcTemplate == null
                || identityRepository == null
                || isBlank(loginSessionId)
                || isBlank(connectionId)
                || isBlank(fromUserId)) {
            return;
        }

        try {
            Optional<LoginSessionBinding> loginSession = findWaitingOrBoundSession(jdbcTemplate, loginSessionId);
            if (loginSession.isEmpty()) {
                return;
            }
            Instant now = boundAt == null ? clock.instant() : boundAt;
            MedicalRole role = MedicalRole.from(loginSession.get().requestedRole());
            String sessionKey = "clawbot:" + connectionId + ":" + fromUserId;
            MedicalUser user = identityRepository.registerWechatUser(
                    connectionId,
                    fromUserId,
                    sessionKey,
                    fallbackDisplayName(displayName, role, fromUserId),
                    role,
                    now);
            jdbcTemplate.update("""
                    UPDATE medical_login_sessions
                    SET status = 'BOUND', bound_user_id = ?, bound_at = ?
                    WHERE login_session_id = ?
                    """, user.id(), Timestamp.from(now), loginSessionId);
        } catch (RuntimeException exception) {
            log.warn("绑定医疗身份登录会话失败，不影响微信消息处理，loginSessionId={}, fromUserId={}, error={}",
                    loginSessionId, fromUserId, rootMessage(exception));
        }
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

    private Optional<LoginSessionBinding> findWaitingOrBoundSession(JdbcTemplate jdbcTemplate, String loginSessionId) {
        return jdbcTemplate.query("""
                SELECT requested_role
                FROM medical_login_sessions
                WHERE login_session_id = ? AND status IN ('WAITING', 'BOUND')
                """, (rs, rowNum) -> new LoginSessionBinding(rs.getString("requested_role")), loginSessionId)
                .stream()
                .findFirst();
    }

    private String fallbackDisplayName(String displayName, MedicalRole role, String fromUserId) {
        String clean = displayName == null ? "" : displayName.strip();
        if (!clean.isBlank()) {
            return clean;
        }
        String suffix = fromUserId == null || fromUserId.length() <= 6
                ? ""
                : fromUserId.substring(Math.max(0, fromUserId.length() - 6));
        return role.name().toLowerCase() + (suffix.isBlank() ? "" : "-" + suffix);
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private record LoginSessionBinding(String requestedRole) {
    }
}
