package com.example.spring.wechat.food.model;

import java.math.BigDecimal;

public record FoodOrderItem(
        String productId,
        String skuId,
        String name,
        String specification,
        int quantity,
        BigDecimal unitPrice) {

    public FoodOrderItem {
        productId = safe(productId);
        skuId = safe(skuId);
        name = safe(name);
        specification = safe(specification);
        quantity = Math.max(1, quantity);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
