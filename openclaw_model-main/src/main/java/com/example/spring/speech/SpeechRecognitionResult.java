package com.example.spring.speech;

public record SpeechRecognitionResult(String text, String model, Integer durationMs) {
    public SpeechRecognitionResult {
        text = text == null ? "" : text.trim();
    }
}
