package com.example.spring.wechat.food.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FoodOrderPreview(
        String previewId,
        String userKey,
        String addressId,
        String merchantId,
        String merchantName,
        List<FoodOrderItem> items,
        BigDecimal subtotal,
        BigDecimal packingFee,
        BigDecimal deliveryFee,
        BigDecimal discount,
        BigDecimal total,
        Integer etaMinutes,
        String confirmationToken,
        Instant expiresAt,
        String rawJson) {

    public FoodOrderPreview {
        previewId = safe(previewId);
        userKey = safe(userKey);
        addressId = safe(addressId);
        merchantId = safe(merchantId);
        merchantName = safe(merchantName);
        items = items == null ? List.of() : List.copyOf(items);
        confirmationToken = safe(confirmationToken);
        expiresAt = expiresAt == null ? Instant.now().plusSeconds(300) : expiresAt;
        rawJson = safe(rawJson);
    }

    public boolean expired(Instant now) {
        return !expiresAt.isAfter(now == null ? Instant.now() : now);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
