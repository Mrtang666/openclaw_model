package com.example.spring.wechat.model;


/**
 * 微信 iLink 适配层，负责消息转换、下载和发送。
 */
public record WechatIncomingVoice(
        VoiceSourceType sourceType,
        String sourceReference,
        byte[] bytes,
        String mimeType,
        String fileName,
        Integer durationMs,
        Integer sampleRate,
        String format,
        String embeddedText) {

    public WechatIncomingVoice {
        if (bytes != null) {
            bytes = bytes.clone();
        }
    }

    public boolean hasBytes() {
        return bytes != null && bytes.length > 0;
    }

    public boolean hasEmbeddedText() {
        return embeddedText != null && !embeddedText.isBlank();
    }

    public boolean hasSourceUrl() {
        return sourceReference != null
                && (sourceReference.startsWith("http://") || sourceReference.startsWith("https://"));
    }

    public String sourceUrl() {
        return hasSourceUrl() ? sourceReference : null;
    }
}

