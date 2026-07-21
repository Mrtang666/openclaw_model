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
        List<WechatIncomingVoice> voices,
        List<WechatIncomingFile> files) {

    public WechatIncomingMessage {
        images = images == null ? List.of() : List.copyOf(images);
        voices = voices == null ? List.of() : List.copyOf(voices);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public WechatIncomingMessage(
            String messageId,
            String fromUserId,
            String contextToken,
            String text,
            List<WechatIncomingImage> images,
            List<WechatIncomingVoice> voices) {
        this(messageId, fromUserId, contextToken, text, images, voices, List.of());
    }

    public WechatIncomingMessage(String fromUserId, String text) {
        this(null, fromUserId, null, text, List.of(), List.of(), List.of());
    }

    public WechatIncomingMessage(String fromUserId, String text, List<WechatIncomingImage> images) {
        this(null, fromUserId, null, text, images, List.of(), List.of());
    }

    public WechatIncomingMessage(
            String messageId,
            String fromUserId,
            String contextToken,
            String text,
            List<WechatIncomingImage> images) {
        this(messageId, fromUserId, contextToken, text, images, List.of(), List.of());
    }

    public WechatIncomingMessage(
            String fromUserId,
            String text,
            List<WechatIncomingImage> images,
            List<WechatIncomingVoice> voices) {
        this(null, fromUserId, null, text, images, voices, List.of());
    }

    public WechatIncomingMessage(
            String fromUserId,
            String text,
            List<WechatIncomingImage> images,
            List<WechatIncomingVoice> voices,
            List<WechatIncomingFile> files) {
        this(null, fromUserId, null, text, images, voices, files);
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }

    public boolean hasVoices() {
        return !voices.isEmpty();
    }

    public boolean hasFiles() {
        return !files.isEmpty();
    }
}
