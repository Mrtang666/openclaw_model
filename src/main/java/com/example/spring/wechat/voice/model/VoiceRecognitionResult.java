package com.example.spring.wechat.voice.model;

public record VoiceRecognitionResult(
        String text,
        String language,
        Double confidence,
        Integer durationMs,
        String source) {
}
