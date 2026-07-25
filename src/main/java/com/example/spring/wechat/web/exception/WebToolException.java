package com.example.spring.wechat.web.exception;

/**
 * 网页工具异常。
 */
public class WebToolException extends RuntimeException {

    public WebToolException(String message) {
        super(message);
    }

    public WebToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
