package com.example.spring.wechat.image.generation.model;

/**
 * 图片生成请求参数。
 * prompt 是核心提示词，styleHint 用于补充风格，width/height 控制尺寸，
 * watermark 控制是否带平台水印。
 */
public record ImageGenerationRequest(
        String prompt,
        String styleHint,
        Integer width,
        Integer height,
        Boolean watermark) {

    public ImageGenerationRequest {
        prompt = normalize(prompt);
        styleHint = normalize(styleHint);
    }

    public ImageGenerationRequest(String prompt) {
        this(prompt, null, null, null, null);
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
