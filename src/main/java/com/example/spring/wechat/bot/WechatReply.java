package com.example.spring.wechat.bot;

import com.example.spring.image.generation.ImageGenerationResult;

public record WechatReply(String text, ImageGenerationResult image) {

    public static WechatReply text(String text) {
        return new WechatReply(text, null);
    }

    public static WechatReply textAndImage(String text, ImageGenerationResult image) {
        return new WechatReply(text, image);
    }

    public boolean hasImage() {
        return image != null && image.imageBytes() != null && image.imageBytes().length > 0;
    }
}
