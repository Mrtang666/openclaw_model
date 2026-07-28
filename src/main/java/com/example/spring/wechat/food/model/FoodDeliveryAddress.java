package com.example.spring.wechat.food.model;

import java.time.Instant;

public record FoodDeliveryAddress(
        String addressId,
        String userKey,
        String label,
        String recipientName,
        String recipientPhone,
        String city,
        String district,
        String detail,
        String longitude,
        String latitude,
        boolean defaultAddress,
        Instant lastUsedAt) {

    public FoodDeliveryAddress {
        addressId = safe(addressId);
        userKey = safe(userKey);
        label = safe(label);
        recipientName = safe(recipientName);
        recipientPhone = safe(recipientPhone);
        city = safe(city);
        district = safe(district);
        detail = safe(detail);
        longitude = safe(longitude);
        latitude = safe(latitude);
        lastUsedAt = lastUsedAt == null ? Instant.now() : lastUsedAt;
    }

    public String maskedSummary() {
        String suffix = detail.length() <= 8 ? detail : detail.substring(0, 8) + "...";
        return String.join(" ", city, district, suffix).replaceAll("\\s+", " ").strip();
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
