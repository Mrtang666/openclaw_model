package com.example.spring.wechat.voice.synthesis.model;

public record VoiceSynthesisRequest(
        String text,
        String model,
        String voice,
        String format,
        Integer sampleRate) {

    public VoiceSynthesisRequest {
        text = text == null ? "" : text.strip();
        model = model == null ? "" : model.strip();
        voice = voice == null ? "" : voice.strip();
        format = format == null || format.isBlank() ? "wav" : format.strip().toLowerCase();
        sampleRate = sampleRate == null || sampleRate <= 0 ? 16000 : sampleRate;
    }
}
