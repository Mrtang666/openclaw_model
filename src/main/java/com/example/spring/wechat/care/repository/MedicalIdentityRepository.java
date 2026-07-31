package com.example.spring.wechat.care.repository;

import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.MedicalUser;
import com.example.spring.wechat.care.model.NotificationTarget;
import com.example.spring.wechat.care.model.PatientRelation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Repository
public class MedicalIdentityRepository {

    private static final RowMapper<MedicalUser> USER_MAPPER = (rs, rowNum) -> new MedicalUser(
            rs.getLong("id"), rs.getString("user_code"), rs.getString("display_name"),
            rs.getString("status"), rs.getLong("version"),
            instant(rs.getTimestamp("first_seen_at")), instant(rs.getTimestamp("last_active_at")),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public MedicalIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public MedicalUser registerWechatUser(
            String connectionId,
            String fromUserId,
            String sessionKey,
            String displayName,
            MedicalRole role,
            Instant now) {
        String userCode = userCode(role, fromUserId);
        Optional<MedicalUser> existing = findUserByCode(userCode);
        if (existing.isPresent()) {
            MedicalUser user = existing.get();
            jdbc.update("""
                    UPDATE medical_users SET last_active_at=?, updated_at=?, version=version+1
                    WHERE id=?
                    """, timestamp(now), timestamp(now), user.id());
            jdbc.update("""
                    INSERT INTO medical_user_wechat_bindings
                    (user_id,connection_id,from_user_id,latest_session_key,status,first_seen_at,last_seen_at,created_at,updated_at)
                    VALUES (?,?,?,?,'ACTIVE',?,?,?,?)
                    ON DUPLICATE KEY UPDATE user_id=VALUES(user_id), latest_session_key=VALUES(latest_session_key),
                        status='ACTIVE', last_seen_at=VALUES(last_seen_at), updated_at=VALUES(updated_at)
                    """, user.id(), connectionId, fromUserId, sessionKey,
                    timestamp(now), timestamp(now), timestamp(now), timestamp(now));
            ensureRole(user.id(), role, now);
            return findUserById(user.id()).orElseThrow();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO medical_users
                    (user_code,display_name,status,version,first_seen_at,last_active_at,created_at,updated_at)
                    VALUES (?,?,'ACTIVE',0,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, userCode);
            statement.setString(2, displayName);
            statement.setTimestamp(3, timestamp(now));
            statement.setTimestamp(4, timestamp(now));
            statement.setTimestamp(5, timestamp(now));
            statement.setTimestamp(6, timestamp(now));
            return statement;
        }, keyHolder);
        long userId = requiredKey(keyHolder);
        jdbc.update("""
                INSERT INTO medical_user_wechat_bindings
                (user_id,connection_id,from_user_id,latest_session_key,status,first_seen_at,last_seen_at,created_at,updated_at)
                VALUES (?,?,?,?,'ACTIVE',?,?,?,?)
                """, userId, connectionId, fromUserId, sessionKey,
                timestamp(now), timestamp(now), timestamp(now), timestamp(now));
        ensureRole(userId, role, now);
        return findUserById(userId).orElseThrow();
    }

    private String userCode(MedicalRole role, String fromUserId) {
        return roleCodePrefix(role) + "-" + stableSuffix(roleIdentity(role) + ":" + clean(fromUserId));
    }

    private String roleIdentity(MedicalRole role) {
        if (role == null) {
            return "USER";
        }
        if (role.isFamily()) {
            return "FAMILY";
        }
        return role.name();
    }

    private String roleCodePrefix(MedicalRole role) {
        if (role == null) {
            return "USR";
        }
        if (role == MedicalRole.PATIENT) {
            return "PAT";
        }
        if (role.isFamily()) {
            return "FAM";
        }
        if (role == MedicalRole.DOCTOR) {
            return "DOC";
        }
        if (role == MedicalRole.NURSE) {
            return "NUR";
        }
        if (role == MedicalRole.THERAPIST) {
            return "THP";
        }
        if (role == MedicalRole.DIETITIAN) {
            return "DIT";
        }
        return role.name().substring(0, Math.min(role.name().length(), 3)).toUpperCase(Locale.ROOT);
    }

    private String stableSuffix(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(clean(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 5).toUpperCase(Locale.ROOT);
        } catch (Exception exception) {
            return Integer.toHexString(clean(value).hashCode()).replace("-", "0").toUpperCase(Locale.ROOT);
        }
    }

    @Transactional
    public Optional<MedicalUser> updateDisplayName(long userId, String displayName, Instant now) {
        String clean = clean(displayName);
        if (clean.isBlank()) {
            return Optional.empty();
        }
        int updated = jdbc.update("""
                UPDATE medical_users
                SET display_name=?, last_active_at=?, updated_at=?, version=version+1
                WHERE id=? AND status='ACTIVE'
                """, clean, timestamp(now), timestamp(now), userId);
        if (updated <= 0) {
            return Optional.empty();
        }
        return findUserById(userId);
    }

    public Optional<MedicalUser> findUserById(long userId) {
        return jdbc.query("SELECT * FROM medical_users WHERE id=?", USER_MAPPER, userId).stream().findFirst();
    }

    public Optional<MedicalUser> findUserByCode(String userCode) {
        return jdbc.query("SELECT * FROM medical_users WHERE user_code=?", USER_MAPPER, userCode)
                .stream().findFirst();
    }

    public Optional<MedicalUser> findUserBySessionKey(String sessionKey) {
        return jdbc.query("""
                SELECT u.* FROM medical_users u
                JOIN medical_user_wechat_bindings b ON b.user_id = u.id
                WHERE b.latest_session_key = ? AND b.status = 'ACTIVE' AND u.status = 'ACTIVE'
                ORDER BY b.last_seen_at DESC, b.id DESC
                """, USER_MAPPER, clean(sessionKey)).stream().findFirst();
    }

    public Optional<MedicalRole> findCurrentRoleBySessionKey(String sessionKey) {
        return jdbc.query("""
                SELECT s.requested_role
                FROM medical_login_sessions s
                JOIN medical_user_wechat_bindings b ON b.connection_id = s.connection_id
                WHERE b.latest_session_key = ? AND s.status = 'BOUND'
                ORDER BY s.bound_at DESC, s.id DESC
                LIMIT 1
                """, (rs, rowNum) -> MedicalRole.valueOf(rs.getString("requested_role")), clean(sessionKey))
                .stream().findFirst();
    }

    public List<MedicalRole> listActiveRoles(long userId) {
        return jdbc.query("""
                SELECT role_code FROM medical_user_roles
                WHERE user_id=? AND status='ACTIVE'
                ORDER BY id
                """, (rs, rowNum) -> MedicalRole.valueOf(rs.getString("role_code")), userId);
    }

    public boolean hasActiveRole(long userId, MedicalRole role) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM medical_user_roles
                WHERE user_id=? AND role_code=? AND status='ACTIVE'
                """, Integer.class, userId, role.name());
        return count != null && count > 0;
    }

    public void saveWebSession(String tokenHash, long userId, MedicalRole role, Instant expiresAt, Instant now) {
        jdbc.update("""
                INSERT INTO medical_web_sessions
                (token_hash,user_id,active_role,expires_at,last_seen_at,revoked_at,created_at)
                VALUES (?,?,?,?,?,NULL,?)
                """, tokenHash, userId, role.name(), timestamp(expiresAt), timestamp(now), timestamp(now));
    }

    public Optional<CareActor> findActiveSession(String tokenHash, Instant now) {
        return jdbc.query("""
                SELECT u.id,u.user_code,u.display_name,s.active_role
                FROM medical_web_sessions s
                JOIN medical_users u ON u.id=s.user_id AND u.status='ACTIVE'
                JOIN medical_user_roles r ON r.user_id=s.user_id
                    AND r.role_code=s.active_role AND r.status='ACTIVE'
                WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?
                """, (rs, rowNum) -> new CareActor(
                        rs.getLong("id"), rs.getString("user_code"), rs.getString("display_name"),
                        MedicalRole.valueOf(rs.getString("active_role"))), tokenHash, timestamp(now))
                .stream().findFirst();
    }

    public void touchSession(String tokenHash, Instant now) {
        jdbc.update("UPDATE medical_web_sessions SET last_seen_at=? WHERE token_hash=?",
                timestamp(now), tokenHash);
    }

    public void revokeSession(String tokenHash, Instant now) {
        jdbc.update("UPDATE medical_web_sessions SET revoked_at=? WHERE token_hash=? AND revoked_at IS NULL",
                timestamp(now), tokenHash);
    }

    @Transactional
    public boolean revokeRelation(long patientUserId, long viewerUserId, MedicalRole role, Instant now) {
        int changed = jdbc.update("""
                UPDATE medical_patient_relations
                SET status='REVOKED',version=version+1,updated_at=?
                WHERE patient_user_id=? AND viewer_user_id=? AND relation_role=? AND status='ACTIVE'
                """, timestamp(now), patientUserId, viewerUserId, role.name());
        if (changed > 0) {
            jdbc.update("""
                    UPDATE medical_consents
                    SET status='REVOKED',revoked_at=?,version=version+1,updated_at=?
                    WHERE patient_user_id=? AND grantee_type='USER' AND grantee_id=? AND status='ACTIVE'
                    """, timestamp(now), timestamp(now), patientUserId, viewerUserId);
        }
        return changed > 0;
    }

    @Transactional
    public PatientRelation grantRelation(
            long patientUserId,
            long viewerUserId,
            MedicalRole role,
            String label,
            Set<String> permissions,
            Instant expiresAt,
            Instant now) {
        jdbc.update("""
                INSERT INTO medical_patient_relations
                (viewer_user_id,patient_user_id,relation_role,relation_label,status,version,created_at,updated_at)
                VALUES (?,?,?,?,'ACTIVE',0,?,?)
                ON DUPLICATE KEY UPDATE relation_label=VALUES(relation_label),status='ACTIVE',
                    version=version+1,updated_at=VALUES(updated_at)
                """, viewerUserId, patientUserId, role.name(), clean(label), timestamp(now), timestamp(now));
        long relationId = jdbc.queryForObject("""
                SELECT id FROM medical_patient_relations
                WHERE viewer_user_id=? AND patient_user_id=? AND relation_role=?
                """, Long.class, viewerUserId, patientUserId, role.name());
        for (String permission : permissions) {
            jdbc.update("""
                    INSERT INTO medical_relation_permissions(relation_id,permission_code,expires_at,created_at)
                    VALUES (?,?,?,?)
                    ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at)
                    """, relationId, permission, nullableTimestamp(expiresAt), timestamp(now));
        }
        jdbc.update("""
                INSERT INTO medical_consents
                (patient_user_id,granted_by_user_id,consent_scope,grantee_type,grantee_id,status,
                 granted_at,expires_at,revoked_at,version,created_at,updated_at)
                VALUES (?,?,'CARE_DATA_SHARE','USER',?,'ACTIVE',?,?,NULL,0,?,?)
                ON DUPLICATE KEY UPDATE status='ACTIVE',granted_at=VALUES(granted_at),
                    expires_at=VALUES(expires_at),revoked_at=NULL,version=version+1,updated_at=VALUES(updated_at)
                """, patientUserId, patientUserId, viewerUserId, timestamp(now), nullableTimestamp(expiresAt),
                timestamp(now), timestamp(now));
        return findRelation(relationId).orElseThrow();
    }

    public boolean hasPermission(long actorUserId, long patientUserId, String permission, Instant now) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM medical_patient_relations r
                JOIN medical_relation_permissions p ON p.relation_id=r.id
                WHERE r.viewer_user_id=? AND r.patient_user_id=? AND r.status='ACTIVE'
                  AND p.permission_code=? AND (p.expires_at IS NULL OR p.expires_at>?)
                """, Integer.class, actorUserId, patientUserId, permission, timestamp(now));
        return count != null && count > 0;
    }

    public List<MedicalUser> listAccessiblePatients(long actorUserId, String permission, Instant now) {
        return jdbc.query("""
                SELECT DISTINCT u.*
                FROM medical_patient_relations r
                JOIN medical_relation_permissions p ON p.relation_id=r.id
                JOIN medical_users u ON u.id=r.patient_user_id AND u.status='ACTIVE'
                WHERE r.viewer_user_id=? AND r.status='ACTIVE' AND p.permission_code=?
                  AND (p.expires_at IS NULL OR p.expires_at>?)
                ORDER BY u.display_name,u.id
                """, USER_MAPPER, actorUserId, permission, timestamp(now));
    }

    public List<MedicalUser> listRelatedViewersByRole(long patientUserId, MedicalRole role) {
        return jdbc.query("""
                SELECT DISTINCT u.*
                FROM medical_patient_relations r
                JOIN medical_users u ON u.id=r.viewer_user_id AND u.status='ACTIVE'
                WHERE r.patient_user_id=? AND r.relation_role=? AND r.status='ACTIVE'
                ORDER BY u.display_name,u.id
                """, USER_MAPPER, patientUserId, role.name());
    }

    public List<MedicalUser> listRelatedViewersByRoleAndPermission(
            long patientUserId,
            MedicalRole role,
            String permission,
            Instant now) {
        return jdbc.query("""
                SELECT DISTINCT u.*
                FROM medical_patient_relations r
                JOIN medical_relation_permissions p ON p.relation_id=r.id
                JOIN medical_users u ON u.id=r.viewer_user_id AND u.status='ACTIVE'
                WHERE r.patient_user_id=? AND r.relation_role=? AND r.status='ACTIVE'
                  AND p.permission_code=? AND (p.expires_at IS NULL OR p.expires_at>?)
                ORDER BY u.display_name,u.id
                """, USER_MAPPER, patientUserId, role.name(), permission, timestamp(now));
    }

    public List<NotificationTarget> listNotificationTargets(long patientUserId, String permission, Instant now) {
        return jdbc.query("""
                SELECT DISTINCT r.viewer_user_id,b.connection_id,b.from_user_id
                FROM medical_patient_relations r
                JOIN medical_relation_permissions p ON p.relation_id=r.id
                JOIN medical_user_wechat_bindings b ON b.user_id=r.viewer_user_id AND b.status='ACTIVE'
                WHERE r.patient_user_id=? AND r.status='ACTIVE' AND p.permission_code=?
                  AND (p.expires_at IS NULL OR p.expires_at>?)
                """, (rs, rowNum) -> new NotificationTarget(
                        rs.getLong("viewer_user_id"), rs.getString("connection_id"), rs.getString("from_user_id")),
                patientUserId, permission, timestamp(now));
    }

    public List<NotificationTarget> listNotificationTargetsByRole(
            long patientUserId,
            MedicalRole role,
            String permission,
            Instant now) {
        return jdbc.query("""
                SELECT r.viewer_user_id,b.connection_id,b.from_user_id
                FROM medical_patient_relations r
                JOIN medical_relation_permissions p ON p.relation_id=r.id
                JOIN medical_user_roles ur ON ur.user_id=r.viewer_user_id
                    AND ur.role_code=? AND ur.status='ACTIVE'
                JOIN medical_user_wechat_bindings b ON b.user_id=r.viewer_user_id AND b.status='ACTIVE'
                WHERE r.patient_user_id=? AND r.status='ACTIVE' AND p.permission_code=?
                  AND (p.expires_at IS NULL OR p.expires_at>?)
                ORDER BY b.last_seen_at DESC,b.id DESC
                """, (rs, rowNum) -> new NotificationTarget(
                        rs.getLong("viewer_user_id"), rs.getString("connection_id"), rs.getString("from_user_id")),
                role.name(), patientUserId, permission, timestamp(now));
    }

    public List<NotificationTarget> listUserNotificationTargets(long userId) {
        return jdbc.query("""
                SELECT user_id,connection_id,from_user_id
                FROM medical_user_wechat_bindings
                WHERE user_id=? AND status='ACTIVE'
                ORDER BY last_seen_at DESC,id DESC
                LIMIT 1
                """, (rs, rowNum) -> new NotificationTarget(
                        rs.getLong("user_id"), rs.getString("connection_id"), rs.getString("from_user_id")),
                userId);
    }

    private Optional<PatientRelation> findRelation(long relationId) {
        return jdbc.query("SELECT * FROM medical_patient_relations WHERE id=?", (rs, rowNum) -> {
            Set<String> permissions = new LinkedHashSet<>(jdbc.queryForList(
                    "SELECT permission_code FROM medical_relation_permissions WHERE relation_id=? ORDER BY permission_code",
                    String.class, relationId));
            return new PatientRelation(
                    rs.getLong("id"), rs.getLong("viewer_user_id"), rs.getLong("patient_user_id"),
                    MedicalRole.valueOf(rs.getString("relation_role")), rs.getString("relation_label"),
                    rs.getString("status"), rs.getLong("version"), Set.copyOf(permissions),
                    instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));
        }, relationId).stream().findFirst();
    }

    private void ensureRole(long userId, MedicalRole role, Instant now) {
        jdbc.update("""
                INSERT INTO medical_user_roles(user_id,role_code,status,created_at,updated_at)
                VALUES (?,?,'ACTIVE',?,?)
                ON DUPLICATE KEY UPDATE status='ACTIVE',updated_at=VALUES(updated_at)
                """, userId, role.name(), timestamp(now), timestamp(now));
    }

    private static long requiredKey(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("数据库未返回新增医疗用户编号");
        }
        return key.longValue();
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Timestamp nullableTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
