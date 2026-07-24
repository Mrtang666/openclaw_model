package com.example.spring.wechat.payment.model;

import java.math.BigDecimal;
import java.time.Instant;

public record WechatPaymentOrder(String paymentId, String rideOrderId, BigDecimal amount,
                                 String status, String codeUrl, Instant createdAt) {
    public WechatPaymentOrder {
        paymentId = paymentId == null ? "" : paymentId.strip();
        rideOrderId = rideOrderId == null ? "" : rideOrderId.strip();
        status = status == null ? "CREATED" : status.strip();
        codeUrl = codeUrl == null ? "" : codeUrl.strip();
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
