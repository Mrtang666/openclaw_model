package com.example.spring.wechat.netdisk.auth;

import com.example.spring.wechat.netdisk.config.BaiduNetdiskProperties;
import com.example.spring.wechat.netdisk.model.NetdiskAuthCallbackResult;
import com.example.spring.wechat.netdisk.model.NetdiskAuthPrompt;
import com.example.spring.wechat.netdisk.model.NetdiskAuthState;
import com.example.spring.wechat.netdisk.model.NetdiskAuthorization;
import com.example.spring.wechat.netdisk.model.NetdiskOAuthToken;
import com.example.spring.wechat.netdisk.model.NetdiskPendingAction;
import com.example.spring.wechat.netdisk.repository.NetdiskAuthorizationRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BaiduNetdiskAuthServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void startBindCreatesAuthStateAndReturnsAuthorizationUrl() {
        FakeRepository repository = new FakeRepository();
        RecordingOAuthClient oauthClient = new RecordingOAuthClient();
        BaiduNetdiskAuthService service = new BaiduNetdiskAuthService(repository, oauthClient, properties(), CLOCK);

        NetdiskAuthPrompt prompt = service.startBind("wx-user-1", "BIND", "/after-auth", null);

        assertThat(repository.states).hasSize(1);
        NetdiskAuthState state = repository.states.values().iterator().next();
        assertThat(state.userId()).isEqualTo("wx-user-1");
        assertThat(state.provider()).isEqualTo("baidu");
        assertThat(state.operation()).isEqualTo("BIND");
        assertThat(state.redirectAfterAuth()).isEqualTo("/after-auth");
        assertThat(state.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(prompt.authorizationUrl()).isEqualTo("https://auth.example.com?state=" + state.state());
        assertThat(prompt.expiresAt()).isEqualTo(state.expiresAt());
    }

    @Test
    void completeAuthorizationEncryptsAndStoresTokenForStateUser() {
        FakeRepository repository = new FakeRepository();
        RecordingOAuthClient oauthClient = new RecordingOAuthClient();
        BaiduNetdiskAuthService service = new BaiduNetdiskAuthService(repository, oauthClient, properties(), CLOCK);
        repository.saveAuthState(new NetdiskAuthState(
                0L,
                "state-1",
                "wx-user-1",
                "baidu",
                "BIND",
                "",
                null,
                NOW.plus(Duration.ofMinutes(10)),
                false,
                NOW));
        oauthClient.exchangeResult = new NetdiskOAuthToken(
                "access-token-1",
                "refresh-token-1",
                3600,
                "basic netdisk",
                NOW.plus(Duration.ofHours(1)));

        NetdiskAuthCallbackResult result = service.completeAuthorization("state-1", "code-1");

        assertThat(result.success()).isTrue();
        assertThat(result.userId()).isEqualTo("wx-user-1");
        assertThat(oauthClient.exchangedCode).isEqualTo("code-1");
        assertThat(repository.savedAuthorization).isNotNull();
        assertThat(repository.savedAuthorization.accessTokenEncrypted()).isNotEqualTo("access-token-1");
        assertThat(new NetdiskTokenCryptoService(properties().tokenEncryptionKey())
                .decrypt(repository.savedAuthorization.accessTokenEncrypted())).isEqualTo("access-token-1");
        assertThat(repository.states.get("state-1").used()).isTrue();
    }

    private BaiduNetdiskProperties properties() {
        return new BaiduNetdiskProperties(
                true,
                "client-id",
                "app-key",
                "",
                "client-secret",
                "sign-key",
                "https://openclaw.example.com/api/netdisk/baidu/callback",
                "https://openapi.baidu.com/oauth/2.0/authorize",
                "https://openapi.baidu.com/oauth/2.0/token",
                "https://mcp-pan.baidu.com/sse",
                "test-encryption-key",
                10,
                30,
                20_000,
                5,
                "/OpenClaw/");
    }

    private static final class RecordingOAuthClient implements NetdiskOAuthClient {

        private NetdiskOAuthToken exchangeResult;
        private String exchangedCode;

        @Override
        public String buildAuthorizationUrl(String state) {
            return "https://auth.example.com?state=" + state;
        }

        @Override
        public NetdiskOAuthToken exchangeCode(String code) {
            this.exchangedCode = code;
            return exchangeResult;
        }

        @Override
        public NetdiskOAuthToken refreshToken(String refreshToken) {
            throw new UnsupportedOperationException("refresh is not used in this test");
        }
    }

    private static final class FakeRepository implements NetdiskAuthorizationRepository {

        private final Map<String, NetdiskAuthState> states = new HashMap<>();
        private NetdiskAuthorization savedAuthorization;
        private long nextId = 1L;

        @Override
        public Optional<NetdiskAuthorization> findActive(String userId, String provider) {
            return Optional.ofNullable(savedAuthorization)
                    .filter(auth -> auth.userId().equals(userId)
                            && auth.provider().equals(provider)
                            && auth.status().equals("ACTIVE"));
        }

        @Override
        public NetdiskAuthorization saveOrUpdate(NetdiskAuthorization authorization) {
            savedAuthorization = new NetdiskAuthorization(
                    nextId++,
                    authorization.userId(),
                    authorization.provider(),
                    authorization.accessTokenEncrypted(),
                    authorization.refreshTokenEncrypted(),
                    authorization.expiresAt(),
                    authorization.scope(),
                    authorization.status(),
                    authorization.createdAt(),
                    authorization.updatedAt());
            return savedAuthorization;
        }

        @Override
        public Optional<NetdiskAuthorization> findById(long id) {
            return Optional.ofNullable(savedAuthorization).filter(auth -> auth.id() == id);
        }

        @Override
        public Optional<NetdiskAuthState> findAuthState(String state) {
            return Optional.ofNullable(states.get(state));
        }

        @Override
        public NetdiskAuthState saveAuthState(NetdiskAuthState state) {
            NetdiskAuthState saved = new NetdiskAuthState(
                    nextId++,
                    state.state(),
                    state.userId(),
                    state.provider(),
                    state.operation(),
                    state.redirectAfterAuth(),
                    state.pendingActionId(),
                    state.expiresAt(),
                    state.used(),
                    state.createdAt());
            states.put(saved.state(), saved);
            return saved;
        }

        @Override
        public void markAuthStateUsed(long id) {
            states.replaceAll((key, value) -> value.id() == id
                    ? new NetdiskAuthState(
                    value.id(),
                    value.state(),
                    value.userId(),
                    value.provider(),
                    value.operation(),
                    value.redirectAfterAuth(),
                    value.pendingActionId(),
                    value.expiresAt(),
                    true,
                    value.createdAt())
                    : value);
        }

        @Override
        public NetdiskPendingAction savePendingAction(NetdiskPendingAction action) {
            throw new UnsupportedOperationException("pending action is not used in this test");
        }

        @Override
        public Optional<NetdiskPendingAction> findPendingAction(long id) {
            return Optional.empty();
        }

        @Override
        public void markPendingActionRunning(long id, Instant updatedAt) {
        }

        @Override
        public void markPendingActionDone(long id, String resultMessage, Instant updatedAt) {
        }

        @Override
        public void markPendingActionFailed(long id, String errorMessage, Instant updatedAt) {
        }
    }
}
