package com.example.spring.wechat.netdisk.auth;

import com.example.spring.wechat.netdisk.config.BaiduNetdiskProperties;
import com.example.spring.wechat.netdisk.exception.NetdiskToolException;
import com.example.spring.wechat.netdisk.model.NetdiskAuthCallbackResult;
import com.example.spring.wechat.netdisk.model.NetdiskAuthPrompt;
import com.example.spring.wechat.netdisk.model.NetdiskAuthState;
import com.example.spring.wechat.netdisk.model.NetdiskAuthorization;
import com.example.spring.wechat.netdisk.model.NetdiskOAuthToken;
import com.example.spring.wechat.netdisk.repository.NetdiskAuthorizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 百度网盘授权业务服务。
 *
 * <p>这一层负责把“微信用户”和“百度 OAuth token”关联起来：
 * 先生成 state 和授权链接，用户授权回调后校验 state，再把 token 加密保存到 MySQL。</p>
 */
@Service
public class BaiduNetdiskAuthService {

    public static final String PROVIDER = "baidu";

    private final NetdiskAuthorizationRepository repository;
    private final NetdiskOAuthClient oauthClient;
    private final BaiduNetdiskProperties properties;
    private final Clock clock;

    @Autowired
    public BaiduNetdiskAuthService(
            NetdiskAuthorizationRepository repository,
            NetdiskOAuthClient oauthClient,
            BaiduNetdiskProperties properties) {
        this(repository, oauthClient, properties, Clock.systemDefaultZone());
    }

    BaiduNetdiskAuthService(
            NetdiskAuthorizationRepository repository,
            NetdiskOAuthClient oauthClient,
            BaiduNetdiskProperties properties,
            Clock clock) {
        this.repository = repository;
        this.oauthClient = oauthClient;
        this.properties = properties;
        this.clock = clock;
    }

    public NetdiskAuthPrompt startBind(String userId, String operation, String redirectAfterAuth, Long pendingActionId) {
        ensureEnabled();
        String stateValue = UUID.randomUUID().toString().replace("-", "");
        Instant now = clock.instant();
        NetdiskAuthState state = repository.saveAuthState(new NetdiskAuthState(
                0L,
                stateValue,
                normalize(userId),
                PROVIDER,
                normalize(operation).isBlank() ? "BIND" : normalize(operation).toUpperCase(),
                normalize(redirectAfterAuth),
                pendingActionId,
                now.plusSeconds(properties.authStateTtlMinutes() * 60L),
                false,
                now));
        return new NetdiskAuthPrompt(state.state(), oauthClient.buildAuthorizationUrl(state.state()), state.expiresAt());
    }

    public NetdiskAuthCallbackResult completeAuthorization(String stateValue, String code) {
        ensureEnabled();
        NetdiskAuthState state = repository.findAuthState(normalize(stateValue))
                .orElseThrow(() -> new NetdiskToolException("授权状态不存在或已经过期，请重新发起百度网盘绑定"));
        if (state.used()) {
            throw new NetdiskToolException("这个百度网盘授权链接已经使用过，请重新发起绑定");
        }
        if (state.expiresAt().isBefore(clock.instant())) {
            throw new NetdiskToolException("百度网盘授权链接已过期，请重新发起绑定");
        }
        if (normalize(code).isBlank()) {
            throw new NetdiskToolException("百度网盘授权回调缺少 code");
        }

        NetdiskOAuthToken token = oauthClient.exchangeCode(normalize(code));
        NetdiskTokenCryptoService cryptoService = new NetdiskTokenCryptoService(properties.tokenEncryptionKey());
        Instant now = clock.instant();
        Instant expiresAt = token.expiresAt() != null
                ? token.expiresAt()
                : now.plusSeconds(Math.max(token.expiresInSeconds(), 0));

        repository.saveOrUpdate(new NetdiskAuthorization(
                0L,
                state.userId(),
                state.provider(),
                cryptoService.encrypt(token.accessToken()),
                cryptoService.encrypt(token.refreshToken()),
                expiresAt,
                token.scope(),
                "ACTIVE",
                now,
                now));
        repository.markAuthStateUsed(state.id());
        return new NetdiskAuthCallbackResult(true, state.userId(), state.pendingActionId(), "百度网盘授权成功");
    }

    public Optional<NetdiskAuthorization> findActiveAuthorization(String userId) {
        return repository.findActive(normalize(userId), PROVIDER);
    }

    public String bindingStatus(String userId) {
        return findActiveAuthorization(userId)
                .map(authorization -> "已绑定百度网盘，可以继续执行网盘操作。")
                .orElse("尚未绑定百度网盘，请先完成授权。");
    }

    private void ensureEnabled() {
        if (!properties.enabled()) {
            throw new NetdiskToolException("百度网盘工具未启用，请检查 BAIDU_NETDISK_ENABLED");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
