package com.example.spring.wechat.voice.recognition.model;


/**
 * 微信语音识别数据模型，负责承载识别请求和结果。
 */
public record VoiceRecognitionResult(
        String text,
        String language,
        Double confidence,
        Integer durationMs,
        String source) {
}

