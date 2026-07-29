package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.config.CareProperties;
import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.MedicalUser;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

@Service
public class CareSessionService {

    private final MedicalIdentityRepository identityRepository;
    private final CareProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public CareSessionService(
            MedicalIdentityRepository identityRepository,
            CareProperties properties,
            Clock clock) {
        this.identityRepository = identityRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedSession bootstrap(BootstrapCommand command, String suppliedKey) {
        if (!properties.bootstrapEnabled()) {
            throw new CareException(CareErrorCode.CONFIGURATION_ERROR, "照护账号初始化入口未启用");
        }
        if (!constantTimeEquals(properties.bootstrapKey(), clean(suppliedKey))) {
            throw new CareException(CareErrorCode.FORBIDDEN, "初始化密钥无效");
        }
        if (command == null) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "缺少账号初始化参数");
        }
        String connectionId = required(command.connectionId(), "connectionId 不能为空");
        String fromUserId = required(command.fromUserId(), "fromUserId 不能为空");
        String displayName = required(command.displayName(), "displayName 不能为空");
        MedicalRole role = parseRole(command.role());
        String sessionKey = "clawbot:" + connectionId + ":" + fromUserId;
        Instant now = clock.instant();
        MedicalUser user = identityRepository.registerWechatUser(
                connectionId, fromUserId, sessionKey, limit(displayName, 255), role, now);
        return issue(user, role, now);
    }

    public IssuedSession issue(MedicalUser user, MedicalRole role, Instant now) {
        if (user == null || !identityRepository.hasActiveRole(user.id(), role)) {
            throw new CareException(CareErrorCode.FORBIDDEN, "用户没有对应的有效角色");
        }
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plusSeconds(properties.sessionTtlHours() * 3600L);
        identityRepository.saveWebSession(sha256(token), user.id(), role, expiresAt, now);
        return new IssuedSession(token, expiresAt, new CareActor(user.id(), user.userCode(), user.displayName(), role));
    }

    public CareActor authenticate(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        Instant now = clock.instant();
        String hash = sha256(token);
        CareActor actor = identityRepository.findActiveSession(hash, now)
                .orElseThrow(() -> new CareException(CareErrorCode.UNAUTHORIZED, "登录已失效，请重新登录"));
        identityRepository.touchSession(hash, now);
        return actor;
    }

    public void logout(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        identityRepository.revokeSession(sha256(token), clock.instant());
    }

    private String bearerToken(String header) {
        String value = clean(header);
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7) || value.length() <= 7) {
            throw new CareException(CareErrorCode.UNAUTHORIZED, "缺少有效的 Bearer Token");
        }
        return value.substring(7).strip();
    }

    private MedicalRole parseRole(String value) {
        try {
            return MedicalRole.from(value);
        } catch (IllegalArgumentException exception) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, exception.getMessage());
        }
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成会话摘要", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private String required(String value, String message) {
        String text = clean(value);
        if (text.isBlank()) throw new CareException(CareErrorCode.INVALID_ARGUMENT, message);
        return text;
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record BootstrapCommand(String connectionId, String fromUserId, String displayName, String role) {
    }

    public record IssuedSession(String accessToken, Instant expiresAt, CareActor actor) {
    }
}
