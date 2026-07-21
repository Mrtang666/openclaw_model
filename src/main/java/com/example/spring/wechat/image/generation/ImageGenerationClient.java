package com.example.spring.wechat.image.generation;

import com.example.spring.wechat.image.generation.model.ImageGenerationRequest;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;

/**
 * 图片生成客户端接口。
 * 屏蔽具体模型平台的 HTTP 调用细节，让上层服务只关心输入请求和图片生成结果。
 */
public interface ImageGenerationClient {

    ImageGenerationResult generate(ImageGenerationRequest request);
}
