package com.example.spring.wechat.commerce.advice.model;

import java.math.BigDecimal;

public record ShoppingAdviceRequest(
        String product,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        String usage,
        String preferences,
        String constraints) {

    public ShoppingAdviceRequest {
        product = product == null ? "" : product.strip();
        usage = usage == null ? "" : usage.strip();
        preferences = preferences == null ? "" : preferences.strip();
        constraints = constraints == null ? "" : constraints.strip();
    }
}
