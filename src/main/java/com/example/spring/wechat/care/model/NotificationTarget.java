package com.example.spring.wechat.care.model;

public record NotificationTarget(long userId, String connectionId, String recipientId) {
}
