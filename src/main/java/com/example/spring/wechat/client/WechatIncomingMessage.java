package com.example.spring.wechat.client;

import java.util.List;

public record WechatIncomingMessage(
        String messageId,
        String fromUserId,
        String contextToken,
        String text,
        List<WechatIncomingImage> images) {

    public WechatIncomingMessage {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public WechatIncomingMessage(String fromUserId, String text) {
        this(null, fromUserId, null, text, List.of());
    }

    public WechatIncomingMessage(String fromUserId, String text, List<WechatIncomingImage> images) {
        this(null, fromUserId, null, text, images);
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }
}
