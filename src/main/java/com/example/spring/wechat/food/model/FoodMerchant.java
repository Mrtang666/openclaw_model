package com.example.spring.wechat.food.model;

import java.math.BigDecimal;

public record FoodMerchant(
        String merchantId,
        String name,
        boolean open,
        BigDecimal minimumOrder,
        BigDecimal deliveryFee,
        Integer etaMinutes,
        String description) {

    public FoodMerchant {
        merchantId = safe(merchantId);
        name = safe(name);
        description = safe(description);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
