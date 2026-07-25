package com.example.spring.wechat.netdisk.model;

import java.time.Instant;

/**
 * 网盘授权状态。
 */
public record NetdiskAuthState(
        long id,
        String state,
        String userId,
        String provider,
        String operation,
        String redirectAfterAuth,
        Long pendingActionId,
        Instant expiresAt,
        boolean used,
        Instant createdAt) {
}
