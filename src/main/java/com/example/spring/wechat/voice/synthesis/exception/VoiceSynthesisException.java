package com.example.spring.wechat.voice.synthesis.exception;

public class VoiceSynthesisException extends RuntimeException {

    public VoiceSynthesisException(String message) {
        super(message);
    }

    public VoiceSynthesisException(String message, Throwable cause) {
        super(message, cause);
    }
}
