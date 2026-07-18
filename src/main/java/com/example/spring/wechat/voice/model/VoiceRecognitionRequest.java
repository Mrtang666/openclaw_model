package com.example.spring.wechat.voice.model;

public record VoiceRecognitionRequest(
        byte[] audioBytes,
        String sourceUrl,
        String fileName,
        String contentType,
        String format,
        Integer sampleRate,
        Integer durationMs,
        String language) {

    public VoiceRecognitionRequest {
        if (audioBytes != null) {
            audioBytes = audioBytes.clone();
        }
    }
}
