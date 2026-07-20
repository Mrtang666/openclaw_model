package com.example.spring.speech;

import java.util.Objects;

public record VoiceAsset(
    byte[] data,
    String format,
    Integer sampleRate,
    Integer bitsPerSample,
    Integer durationMs) {

    public VoiceAsset {
        Objects.requireNonNull(data, "data");
        data = data.clone();
        format = format == null || format.isBlank() ? "unknown" : format.toLowerCase();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
