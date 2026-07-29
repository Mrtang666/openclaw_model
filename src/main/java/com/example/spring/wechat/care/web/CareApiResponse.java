package com.example.spring.wechat.care.web;

import java.time.Instant;

public record CareApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        Instant timestamp) {

    public static <T> CareApiResponse<T> success(T data, String traceId) {
        return new CareApiResponse<>("OK", "success", data, traceId, Instant.now());
    }

    public static CareApiResponse<Void> error(String code, String message, String traceId) {
        return new CareApiResponse<>(code, message, null, traceId, Instant.now());
    }
}
