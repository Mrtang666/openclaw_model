package com.example.spring.wechat.netdisk.repository;

import com.example.spring.wechat.netdisk.model.NetdiskAuthState;
import com.example.spring.wechat.netdisk.model.NetdiskAuthorization;
import com.example.spring.wechat.netdisk.model.NetdiskPendingAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
class NetdiskAuthorizationRepositoryTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("openclaw_test");
        jdbcTemplate.update("DELETE FROM netdisk_operation_logs");
        jdbcTemplate.update("DELETE FROM netdisk_pending_actions");
        jdbcTemplate.update("DELETE FROM netdisk_auth_states");
        jdbcTemplate.update("DELETE FROM user_netdisk_authorizations");
    }

    @Test
    void storesAndReadsActiveAuthorizationPerWechatUser() {
        NetdiskAuthorizationRepository repository = repository();
        Instant now = Instant.parse("2026-07-24T00:00:00Z");

        repository.saveOrUpdate(new NetdiskAuthorization(
                0L,
                "wx-user-1",
                "baidu",
                "enc-access",
                "enc-refresh",
                now.plus(Duration.ofHours(1)),
                "scope",
                "ACTIVE",
                now,
                now));

        Optional<NetdiskAuthorization> authorization = repository.findActive("wx-user-1", "baidu");

        assertThat(authorization).isPresent();
        assertThat(authorization.orElseThrow().accessTokenEncrypted()).isEqualTo("enc-access");
    }

    @Test
    void storesAndMarksPendingActionLifecycle() {
        NetdiskAuthorizationRepository repository = repository();
        Instant now = Instant.parse("2026-07-24T00:00:00Z");

        NetdiskPendingAction saved = repository.savePendingAction(new NetdiskPendingAction(
                0L,
                "wx-user-1",
                "baidu",
                "SAVE_CONTENT",
                "{\"tool\":\"netdisk_save\"}",
                "PENDING",
                "",
                now.plus(Duration.ofMinutes(30)),
                now,
                now));

        assertThat(saved.id()).isPositive();
        assertThat(repository.findPendingAction(saved.id())).isPresent();

        repository.markPendingActionRunning(saved.id(), now.plusSeconds(5));
        assertThat(repository.findPendingAction(saved.id())).get().extracting(NetdiskPendingAction::status)
                .isEqualTo("RUNNING");
    }

    @Test
    void storesAndReadsAuthState() {
        NetdiskAuthorizationRepository repository = repository();
        Instant now = Instant.parse("2026-07-24T00:00:00Z");

        NetdiskAuthState state = repository.saveAuthState(new NetdiskAuthState(
                0L,
                "state-1",
                "wx-user-1",
                "baidu",
                "BIND",
                "/after-auth",
                42L,
                now.plus(Duration.ofMinutes(10)),
                false,
                now));

        assertThat(state.id()).isPositive();
        assertThat(repository.findAuthState("state-1")).isPresent();
    }

    private NetdiskAuthorizationRepository repository() {
        try {
            return applicationContext.getBean(NetdiskAuthorizationRepository.class);
        } catch (Exception exception) {
            fail("NetdiskAuthorizationRepository bean 尚未实现");
            return null;
        }
    }
}
