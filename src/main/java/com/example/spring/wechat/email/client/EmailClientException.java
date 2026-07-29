package com.example.spring.wechat.email.client;

public class EmailClientException extends RuntimeException {

    public EmailClientException(String message) {
        super(message);
    }

    public EmailClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
