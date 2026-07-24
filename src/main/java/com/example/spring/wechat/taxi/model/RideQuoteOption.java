package com.example.spring.wechat.taxi.model;

import java.math.BigDecimal;

public record RideQuoteOption(
        String optionId,
        String productCategory,
        String name,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer durationSeconds,
        String rawJson) {

    public RideQuoteOption {
        optionId = clean(optionId);
        productCategory = clean(productCategory);
        name = clean(name);
        rawJson = clean(rawJson);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
