package com.example.spring.wechat.food.model;

import java.time.Instant;
import java.util.List;

public record FoodOrderDraft(
        String userKey,
        String addressId,
        String merchantId,
        String merchantName,
        List<FoodOrderItem> items,
        String remark,
        Instant updatedAt) {

    public FoodOrderDraft {
        userKey = safe(userKey);
        addressId = safe(addressId);
        merchantId = safe(merchantId);
        merchantName = safe(merchantName);
        items = items == null ? List.of() : List.copyOf(items);
        remark = safe(remark);
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
