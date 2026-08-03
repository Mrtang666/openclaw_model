package com.example.spring.wechat.care.repository;

import com.example.spring.wechat.care.model.CareTaskActionToken;
import com.example.spring.wechat.care.model.MedicalRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class CareTaskActionTokenRepository {

    private static final RowMapper<CareTaskActionToken> MAPPER = (rs, rowNum) -> new CareTaskActionToken(
            rs.getLong("id"), rs.getLong("task_instance_id"), rs.getLong("actor_user_id"),
            MedicalRole.valueOf(rs.getString("actor_role")), rs.getString("token_hash"),
            rs.getTimestamp("expires_at").toInstant(), instant(rs.getTimestamp("used_at")),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public CareTaskActionTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void create(
            long taskId,
            long actorUserId,
            MedicalRole actorRole,
            String tokenHash,
            Instant expiresAt,
            Instant now) {
        jdbc.update("""
                UPDATE medical_care_task_action_tokens
                SET used_at=?,updated_at=?
                WHERE task_instance_id=? AND actor_user_id=? AND actor_role=? AND used_at IS NULL
                """, timestamp(now), timestamp(now), taskId, actorUserId, actorRole.name());
        jdbc.update("""
                INSERT INTO medical_care_task_action_tokens
                (task_instance_id,actor_user_id,actor_role,token_hash,expires_at,used_at,created_at,updated_at)
                VALUES (?,?,?,?,?,NULL,?,?)
                """, taskId, actorUserId, actorRole.name(), tokenHash, timestamp(expiresAt),
                timestamp(now), timestamp(now));
    }

    public Optional<CareTaskActionToken> findActive(String tokenHash, Instant now) {
        return jdbc.query("""
                SELECT * FROM medical_care_task_action_tokens
                WHERE token_hash=? AND used_at IS NULL AND expires_at>=?
                """, MAPPER, tokenHash, timestamp(now)).stream().findFirst();
    }

    public boolean consume(long tokenId, Instant now) {
        return jdbc.update("""
                UPDATE medical_care_task_action_tokens
                SET used_at=?,updated_at=?
                WHERE id=? AND used_at IS NULL AND expires_at>=?
                """, timestamp(now), timestamp(now), tokenId, timestamp(now)) == 1;
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
