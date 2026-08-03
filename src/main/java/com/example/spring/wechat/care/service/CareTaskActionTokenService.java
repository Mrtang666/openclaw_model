package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareTaskActionToken;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.repository.CareTaskActionTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class CareTaskActionTokenService {

    private final CareTaskActionTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public CareTaskActionTokenService(CareTaskActionTokenRepository repository) {
        this.repository = repository;
    }

    public String issue(
            long taskId,
            long actorUserId,
            MedicalRole actorRole,
            Instant expiresAt,
            Instant now) {
        if (actorRole == null || (actorRole != MedicalRole.PATIENT && !actorRole.isFamily())) {
            throw new IllegalArgumentException("task action links only support patient or family roles");
        }
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("task action token expiry must be in the future");
        }
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.create(taskId, actorUserId, actorRole, sha256(rawToken), expiresAt, now);
        return rawToken;
    }

    public CareTaskActionToken requireActive(String rawToken, Instant now) {
        String token = clean(rawToken);
        if (token.isBlank()) {
            throw new CareException(CareErrorCode.UNAUTHORIZED, "任务链接无效");
        }
        return repository.findActive(sha256(token), now)
                .orElseThrow(() -> new CareException(
                        CareErrorCode.UNAUTHORIZED, "任务链接已过期或已经使用"));
    }

    public void consume(CareTaskActionToken token, Instant now) {
        if (token == null || !repository.consume(token.id(), now)) {
            throw new CareException(CareErrorCode.CONFLICT, "任务链接已经使用，请刷新任务状态");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成任务链接摘要", exception);
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
