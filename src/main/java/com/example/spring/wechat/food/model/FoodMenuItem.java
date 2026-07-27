package com.example.spring.wechat.food.model;

import java.math.BigDecimal;
import java.util.List;

public record FoodMenuItem(
        String productId,
        String name,
        BigDecimal price,
        boolean available,
        List<String> specificationGroups,
        String description) {

    public FoodMenuItem {
        productId = safe(productId);
        name = safe(name);
        specificationGroups = specificationGroups == null ? List.of() : List.copyOf(specificationGroups);
        description = safe(description);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
