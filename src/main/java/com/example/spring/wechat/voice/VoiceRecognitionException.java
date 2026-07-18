package com.example.spring.wechat.voice;

public class VoiceRecognitionException extends RuntimeException {

    public VoiceRecognitionException(String message) {
        super(message);
    }

    public VoiceRecognitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
