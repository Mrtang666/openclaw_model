package com.example.spring.wechat.food.model;

public enum FoodOrderStatus {
    ORDER_CREATED,
    AWAITING_PAYMENT,
    PAID,
    MERCHANT_CONFIRMED,
    PREPARING,
    RIDER_ASSIGNED,
    PICKED_UP,
    DELIVERING,
    DELIVERED,
    MERCHANT_REJECTED,
    CANCEL_PENDING,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED,
    CLOSED
}
