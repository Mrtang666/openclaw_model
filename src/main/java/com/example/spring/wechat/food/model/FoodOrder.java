package com.example.spring.wechat.food.model;

import java.math.BigDecimal;
import java.time.Instant;

public record FoodOrder(
        String orderId,
        String providerOrderId,
        String userKey,
        String previewId,
        String merchantName,
        FoodOrderStatus status,
        BigDecimal total,
        Integer etaMinutes,
        String progressText,
        String rawJson,
        Instant updatedAt) {

    public FoodOrder {
        orderId = safe(orderId);
        providerOrderId = safe(providerOrderId);
        userKey = safe(userKey);
        previewId = safe(previewId);
        merchantName = safe(merchantName);
        status = status == null ? FoodOrderStatus.ORDER_CREATED : status;
        progressText = safe(progressText);
        rawJson = safe(rawJson);
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
