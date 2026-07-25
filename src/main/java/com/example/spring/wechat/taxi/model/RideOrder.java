package com.example.spring.wechat.taxi.model;

import java.math.BigDecimal;
import java.time.Instant;

public record RideOrder(
        String orderId,
        String sessionKey,
        String quoteId,
        String productCategory,
        RideOrderStatus status,
        String driverName,
        String driverPhone,
        String vehiclePlate,
        Integer etaSeconds,
        BigDecimal finalFare,
        String rawJson,
        Instant updatedAt) {

    public RideOrder {
        orderId = clean(orderId);
        sessionKey = clean(sessionKey);
        quoteId = clean(quoteId);
        productCategory = clean(productCategory);
        status = status == null ? RideOrderStatus.DRIVER_SEARCHING : status;
        driverName = clean(driverName);
        driverPhone = clean(driverPhone);
        vehiclePlate = clean(vehiclePlate);
        rawJson = clean(rawJson);
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    public RideOrder withStatus(RideOrderStatus nextStatus, String nextRawJson) {
        return new RideOrder(orderId, sessionKey, quoteId, productCategory, nextStatus,
                driverName, driverPhone, vehiclePlate, etaSeconds, finalFare,
                nextRawJson == null || nextRawJson.isBlank() ? rawJson : nextRawJson, Instant.now());
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
