package com.example.spring.wechat.image.generation;

/**
 * 图片生成模块的业务异常。
 * 用于统一包装参数错误、模型平台调用失败、图片下载失败等问题，方便上层给用户友好提示。
 */
public class ImageGenerationException extends RuntimeException {

    public ImageGenerationException(String message) {
        super(message);
    }

    public ImageGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
