package com.example.spring.wechat.image.model;


/**
 * 微信图片理解数据模型，负责承载图片分析请求。
 */
import com.example.spring.wechat.model.WechatIncomingImage;

import java.util.List;

public record ImageAnalysisRequest(String userText, List<WechatIncomingImage> images) {

    public ImageAnalysisRequest {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }
}

