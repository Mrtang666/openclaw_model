package com.example.spring.speech;

public class SpeechRecognitionException extends Exception {
    public SpeechRecognitionException(String message) {
        super(message);
    }

    public SpeechRecognitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
