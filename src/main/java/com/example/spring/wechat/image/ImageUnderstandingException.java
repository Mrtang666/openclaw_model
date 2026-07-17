package com.example.spring.wechat.image;

public class ImageUnderstandingException extends RuntimeException {

    public ImageUnderstandingException(String message) {
        super(message);
    }

    public ImageUnderstandingException(String message, Throwable cause) {
        super(message, cause);
    }
}
