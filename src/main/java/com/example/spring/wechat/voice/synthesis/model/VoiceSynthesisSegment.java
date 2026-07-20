package com.example.spring.wechat.voice.synthesis.model;

public record VoiceSynthesisSegment(
        byte[] audioBytes,
        String fileName,
        String format,
        String contentType,
        Integer durationMs,
        Integer sampleRate,
        Integer encodeType,
        Integer bitsPerSample,
        String transcriptText) {

    public VoiceSynthesisSegment {
        if (audioBytes != null) {
            audioBytes = audioBytes.clone();
        }
        fileName = fileName == null || fileName.isBlank() ? "reply.silk" : fileName.strip();
        format = format == null || format.isBlank() ? "silk" : format.strip().toLowerCase();
        contentType = contentType == null || contentType.isBlank() ? "audio/" + format : contentType.strip();
        durationMs = durationMs == null || durationMs <= 0 ? 1000 : durationMs;
        sampleRate = sampleRate == null || sampleRate <= 0 ? 16000 : sampleRate;
        encodeType = encodeType == null ? 6 : encodeType;
        bitsPerSample = bitsPerSample == null ? 16 : bitsPerSample;
        transcriptText = transcriptText == null ? "" : transcriptText.strip();
    }
}
