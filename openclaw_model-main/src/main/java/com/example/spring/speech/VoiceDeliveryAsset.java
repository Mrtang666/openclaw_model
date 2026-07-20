package com.example.spring.speech;

public record VoiceDeliveryAsset(
    String text,
    byte[] silkData,
    long durationMs,
    byte[] mp3Data) {
}
