package com.example.spring.wechat.commerce.logistics.model;

public class LogisticsServiceException extends RuntimeException {

    public LogisticsServiceException(String message) {
        super(message);
    }

    public LogisticsServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
