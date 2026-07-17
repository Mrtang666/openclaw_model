package com.example.spring.wechat.image.model;

import com.example.spring.wechat.client.WechatIncomingImage;

import java.util.List;

public record ImageAnalysisRequest(String userText, List<WechatIncomingImage> images) {

    public ImageAnalysisRequest {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }
}
