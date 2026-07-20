package com.example.spring.wechat.model;


/**
 * 微信 iLink 适配层，负责消息转换、下载和发送。
 */
import java.util.List;

public record WechatIncomingMessage(
        String messageId,
        String fromUserId,
        String contextToken,
        String text,
        List<WechatIncomingImage> images,
        List<WechatIncomingVoice> voices) {

    public WechatIncomingMessage {
        images = images == null ? List.of() : List.copyOf(images);
        voices = voices == null ? List.of() : List.copyOf(voices);
    }

    public WechatIncomingMessage(String fromUserId, String text) {
        this(null, fromUserId, null, text, List.of(), List.of());
    }

    public WechatIncomingMessage(String fromUserId, String text, List<WechatIncomingImage> images) {
        this(null, fromUserId, null, text, images, List.of());
    }

    public WechatIncomingMessage(
            String messageId,
            String fromUserId,
            String contextToken,
            String text,
            List<WechatIncomingImage> images) {
        this(messageId, fromUserId, contextToken, text, images, List.of());
    }

    public WechatIncomingMessage(
            String fromUserId,
            String text,
            List<WechatIncomingImage> images,
            List<WechatIncomingVoice> voices) {
        this(null, fromUserId, null, text, images, voices);
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }

    public boolean hasVoices() {
        return !voices.isEmpty();
    }
}

