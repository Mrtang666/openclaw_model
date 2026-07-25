package com.example.spring.wechat.netdisk.repository;

import com.example.spring.wechat.netdisk.model.NetdiskAuthState;
import com.example.spring.wechat.netdisk.model.NetdiskAuthorization;
import com.example.spring.wechat.netdisk.model.NetdiskPendingAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 百度网盘 MySQL 持久化实现。
 */
@Repository
public class MySqlNetdiskAuthorizationRepository implements NetdiskAuthorizationRepository {

    private final JdbcTemplate jdbcTemplate;

    public MySqlNetdiskAuthorizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<NetdiskAuthorization> findActive(String userId, String provider) {
        List<NetdiskAuthorization> list = jdbcTemplate.query(
                """
                        SELECT id, user_id, provider, access_token_encrypted, refresh_token_encrypted,
                               expires_at, scope, status, created_at, updated_at
                        FROM user_netdisk_authorizations
                        WHERE user_id = ? AND provider = ? AND status = 'ACTIVE'
                        ORDER BY updated_at DESC, id DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> mapAuthorization(rs),
                safe(userId),
                safe(provider));
        return list.stream().findFirst();
    }

    @Override
    public NetdiskAuthorization saveOrUpdate(NetdiskAuthorization authorization) {
        Instant now = authorization.updatedAt() == null ? Instant.now() : authorization.updatedAt();
        Long existingId = jdbcTemplate.query(
                """
                        SELECT id
                        FROM user_netdisk_authorizations
                        WHERE user_id = ? AND provider = ?
                        LIMIT 1
                        """,
                rs -> rs.next() ? rs.getLong(1) : null,
                safe(authorization.userId()),
                safe(authorization.provider()));
        if (existingId == null) {
            long id = insertAuthorization(authorization);
            return findById(id).orElseThrow();
        }
        jdbcTemplate.update(
                """
                        UPDATE user_netdisk_authorizations
                        SET access_token_encrypted = ?,
                            refresh_token_encrypted = ?,
                            expires_at = ?,
                            scope = ?,
                            status = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                safe(authorization.accessTokenEncrypted()),
                safe(authorization.refreshTokenEncrypted()),
                timestamp(authorization.expiresAt()),
                safe(authorization.scope()),
                safe(authorization.status()),
                Timestamp.from(now),
                existingId);
        return findById(existingId).orElseThrow();
    }

    @Override
    public Optional<NetdiskAuthorization> findById(long id) {
        List<NetdiskAuthorization> list = jdbcTemplate.query(
                """
                        SELECT id, user_id, provider, access_token_encrypted, refresh_token_encrypted,
                               expires_at, scope, status, created_at, updated_at
                        FROM user_netdisk_authorizations
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapAuthorization(rs),
                id);
        return list.stream().findFirst();
    }

    @Override
    public Optional<NetdiskAuthState> findAuthState(String state) {
        List<NetdiskAuthState> list = jdbcTemplate.query(
                """
                        SELECT id, state, user_id, provider, operation, redirect_after_auth,
                               pending_action_id, expires_at, used, created_at
                        FROM netdisk_auth_states
                        WHERE state = ?
                        """,
                (rs, rowNum) -> mapAuthState(rs),
                safe(state));
        return list.stream().findFirst();
    }

    @Override
    public NetdiskAuthState saveAuthState(NetdiskAuthState state) {
        long id = insertAuthState(state);
        return findAuthState(state.state()).orElseGet(() -> new NetdiskAuthState(
                id,
                state.state(),
                state.userId(),
                state.provider(),
                state.operation(),
                state.redirectAfterAuth(),
                state.pendingActionId(),
                state.expiresAt(),
                state.used(),
                state.createdAt()));
    }

    @Override
    public void markAuthStateUsed(long id) {
        jdbcTemplate.update(
                "UPDATE netdisk_auth_states SET used = 1 WHERE id = ?",
                id);
    }

    @Override
    public NetdiskPendingAction savePendingAction(NetdiskPendingAction action) {
        long id = insertPendingAction(action);
        return findPendingAction(id).orElseGet(() -> new NetdiskPendingAction(
                id,
                action.userId(),
                action.provider(),
                action.actionType(),
                action.payloadJson(),
                action.status(),
                action.errorMessage(),
                action.expiresAt(),
                action.createdAt(),
                action.updatedAt()));
    }

    @Override
    public Optional<NetdiskPendingAction> findPendingAction(long id) {
        List<NetdiskPendingAction> list = jdbcTemplate.query(
                """
                        SELECT id, user_id, provider, action_type, payload_json, status, error_message,
                               expires_at, created_at, updated_at
                        FROM netdisk_pending_actions
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapPendingAction(rs),
                id);
        return list.stream().findFirst();
    }

    @Override
    public void markPendingActionRunning(long id, Instant updatedAt) {
        updatePendingActionStatus(id, "RUNNING", null, updatedAt);
    }

    @Override
    public void markPendingActionDone(long id, String resultMessage, Instant updatedAt) {
        updatePendingActionStatus(id, "DONE", resultMessage, updatedAt);
    }

    @Override
    public void markPendingActionFailed(long id, String errorMessage, Instant updatedAt) {
        updatePendingActionStatus(id, "FAILED", errorMessage, updatedAt);
    }

    private long insertAuthorization(NetdiskAuthorization authorization) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO user_netdisk_authorizations
                            (user_id, provider, access_token_encrypted, refresh_token_encrypted, expires_at, scope, status, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, safe(authorization.userId()));
            statement.setString(2, safe(authorization.provider()));
            statement.setString(3, safe(authorization.accessTokenEncrypted()));
            statement.setString(4, safe(authorization.refreshTokenEncrypted()));
            statement.setTimestamp(5, timestamp(authorization.expiresAt()));
            statement.setString(6, safe(authorization.scope()));
            statement.setString(7, safe(authorization.status()));
            statement.setTimestamp(8, timestamp(authorization.createdAt()));
            statement.setTimestamp(9, timestamp(authorization.updatedAt()));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private long insertAuthState(NetdiskAuthState state) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO netdisk_auth_states
                            (state, user_id, provider, operation, redirect_after_auth, pending_action_id,
                             expires_at, used, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, safe(state.state()));
            statement.setString(2, safe(state.userId()));
            statement.setString(3, safe(state.provider()));
            statement.setString(4, safe(state.operation()));
            statement.setString(5, safe(state.redirectAfterAuth()));
            if (state.pendingActionId() == null) {
                statement.setNull(6, java.sql.Types.BIGINT);
            } else {
                statement.setLong(6, state.pendingActionId());
            }
            statement.setTimestamp(7, timestamp(state.expiresAt()));
            statement.setBoolean(8, state.used());
            statement.setTimestamp(9, timestamp(state.createdAt()));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private long insertPendingAction(NetdiskPendingAction action) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO netdisk_pending_actions
                            (user_id, provider, action_type, payload_json, status, error_message, expires_at, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, safe(action.userId()));
            statement.setString(2, safe(action.provider()));
            statement.setString(3, safe(action.actionType()));
            statement.setString(4, safe(action.payloadJson()));
            statement.setString(5, safe(action.status()));
            statement.setString(6, safe(action.errorMessage()));
            statement.setTimestamp(7, timestamp(action.expiresAt()));
            statement.setTimestamp(8, timestamp(action.createdAt()));
            statement.setTimestamp(9, timestamp(action.updatedAt()));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private void updatePendingActionStatus(long id, String status, String errorMessage, Instant updatedAt) {
        jdbcTemplate.update(
                """
                        UPDATE netdisk_pending_actions
                        SET status = ?, error_message = ?, updated_at = ?
                        WHERE id = ?
                        """,
                safe(status),
                safe(errorMessage),
                timestamp(updatedAt),
                id);
    }

    private NetdiskAuthorization mapAuthorization(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new NetdiskAuthorization(
                rs.getLong(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                toInstant(rs.getTimestamp(6)),
                rs.getString(7),
                rs.getString(8),
                toInstant(rs.getTimestamp(9)),
                toInstant(rs.getTimestamp(10)));
    }

    private NetdiskAuthState mapAuthState(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new NetdiskAuthState(
                rs.getLong(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getObject(7) == null ? null : rs.getLong(7),
                toInstant(rs.getTimestamp(8)),
                rs.getBoolean(9),
                toInstant(rs.getTimestamp(10)));
    }

    private NetdiskPendingAction mapPendingAction(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new NetdiskPendingAction(
                rs.getLong(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7),
                toInstant(rs.getTimestamp(8)),
                toInstant(rs.getTimestamp(9)),
                toInstant(rs.getTimestamp(10)));
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant == null ? Instant.now() : instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
