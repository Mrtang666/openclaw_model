package com.example.spring.wechat.food.model;

import java.time.Instant;

public record FoodPaymentHandoff(
        String handoffId,
        String orderId,
        Type type,
        String target,
        String fallbackTarget,
        Instant expiresAt,
        String status) {

    public FoodPaymentHandoff {
        handoffId = safe(handoffId);
        orderId = safe(orderId);
        type = type == null ? Type.MANUAL : type;
        target = safe(target);
        fallbackTarget = safe(fallbackTarget);
        expiresAt = expiresAt == null ? Instant.now().plusSeconds(600) : expiresAt;
        status = safe(status).isBlank() ? "CREATED" : status.strip();
    }

    public enum Type {
        WECHAT_JSAPI,
        MINI_PROGRAM,
        H5,
        QR_CODE,
        MANUAL
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
