package com.example.spring.wechat.email;

public class EmailToolException extends RuntimeException {

    public EmailToolException(String message) {
        super(message);
    }

    public EmailToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
