package com.example.spring.wechat.netdisk.model;

import java.time.Instant;

/**
 * 网盘授权后待恢复任务。
 */
public record NetdiskPendingAction(
        long id,
        String userId,
        String provider,
        String actionType,
        String payloadJson,
        String status,
        String errorMessage,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {
}
