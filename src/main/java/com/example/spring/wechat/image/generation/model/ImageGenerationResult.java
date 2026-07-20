package com.example.spring.wechat.image.generation.model;

/**
 * 图片生成结果。
 * 同时保存模型返回的图片 URL、下载后的图片字节、文件名和尺寸信息，
 * 方便微信端按图片文件形式发送给用户。
 */
public record ImageGenerationResult(
        String prompt,
        String imageUrl,
        byte[] imageBytes,
        String fileName,
        String contentType,
        Integer width,
        Integer height) {
}
