package com.example.spring.wechat.netdisk.model;

import java.time.Instant;

/**
 * 用户网盘授权信息。
 */
public record NetdiskAuthorization(
        long id,
        String userId,
        String provider,
        String accessTokenEncrypted,
        String refreshTokenEncrypted,
        Instant expiresAt,
        String scope,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
