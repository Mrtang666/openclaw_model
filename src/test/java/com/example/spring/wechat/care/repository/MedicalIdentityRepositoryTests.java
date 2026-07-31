package com.example.spring.wechat.care.repository;

import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.MedicalUser;
import com.example.spring.wechat.care.model.NotificationTarget;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
@Transactional
class MedicalIdentityRepositoryTests {

    @Autowired
    private MedicalIdentityRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void newerPatientLoginDeactivatesPreviousBindingAndBecomesOnlyNotificationTarget() {
        String suffix = UUID.randomUUID().toString();
        String fromUserId = "patient-" + suffix;
        Instant firstSeen = Instant.parse("2026-07-31T09:00:00Z");
        MedicalUser patient = bind("old-" + suffix, "login-old-" + suffix, fromUserId, firstSeen);

        Instant secondSeen = firstSeen.plusSeconds(60);
        MedicalUser rebound = bind("new-" + suffix, "login-new-" + suffix, fromUserId, secondSeen);

        assertThat(rebound.id()).isEqualTo(patient.id());
        assertThat(repository.listUserNotificationTargetsByRole(patient.id(), MedicalRole.PATIENT))
                .extracting(NotificationTarget::connectionId)
                .containsExactly("new-" + suffix);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM medical_user_wechat_bindings
                WHERE user_id=? AND connection_id=?
                """, String.class, patient.id(), "old-" + suffix)).isEqualTo("INACTIVE");
    }

    private MedicalUser bind(String connectionId, String loginSessionId, String fromUserId, Instant now) {
        jdbc.update("""
                INSERT INTO medical_login_sessions
                (connection_id,login_session_id,requested_role,status,bound_user_id,created_at,bound_at)
                VALUES (?,?,'PATIENT','BOUND',NULL,?,?)
                """, connectionId, loginSessionId, Timestamp.from(now), Timestamp.from(now));
        MedicalUser user = repository.registerWechatUser(
                connectionId, fromUserId, "clawbot:" + connectionId + ":" + fromUserId,
                "测试患者", MedicalRole.PATIENT, now);
        jdbc.update("""
                UPDATE medical_login_sessions SET bound_user_id=? WHERE login_session_id=?
                """, user.id(), loginSessionId);
        return user;
    }
}
