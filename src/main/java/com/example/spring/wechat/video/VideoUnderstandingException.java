package com.example.spring.wechat.video;

public class VideoUnderstandingException extends RuntimeException {

    public VideoUnderstandingException(String message) {
        super(message);
    }

    public VideoUnderstandingException(String message, Throwable cause) {
        super(message, cause);
    }
}
