package com.example.spring.wechat.voice.recognition.model;


/**
 * 微信语音识别数据模型，负责承载识别请求和结果。
 */
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

