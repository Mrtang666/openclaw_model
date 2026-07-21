package com.example.spring.wechat.voice.synthesis.model;

public record VoiceSynthesisAudio(
        byte[] audioBytes,
        String format,
        String contentType,
        Integer sampleRate) {

    public VoiceSynthesisAudio {
        if (audioBytes != null) {
            audioBytes = audioBytes.clone();
        }
        format = format == null || format.isBlank() ? "wav" : format.strip().toLowerCase();
        contentType = contentType == null || contentType.isBlank() ? "audio/" + format : contentType.strip();
        sampleRate = sampleRate == null || sampleRate <= 0 ? 16000 : sampleRate;
    }
}
