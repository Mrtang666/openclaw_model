package com.example.spring.speech;

public record SpeechSynthesisResult(
    byte[] data,
    String format,
    int sampleRate,
    int bitsPerSample,
    int channels,
    long durationMs) {
}
