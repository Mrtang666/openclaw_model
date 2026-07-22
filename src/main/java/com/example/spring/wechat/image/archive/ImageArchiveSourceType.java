package com.example.spring.wechat.image.archive;

/**
 * 图片资源来源类型。
 *
 * <p>USER_UPLOAD 表示用户从微信端发来的原始图片；
 * AI_GENERATED 表示系统调用图片生成模型后返回并发送给用户的图片。</p>
 */
public enum ImageArchiveSourceType {

    USER_UPLOAD,
    AI_GENERATED
}
