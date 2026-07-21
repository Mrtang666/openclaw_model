package com.example.spring.wechat.model;


/**
 * 微信 iLink 适配层，负责消息转换、下载和发送。
 */
public record WechatIncomingImage(
        ImageSourceType sourceType,
        String sourceReference,
        byte[] bytes,
        String mimeType,
        String fileName,
        Integer width,
        Integer height,
        String colorMode) {

    public WechatIncomingImage {
        if (bytes != null) {
            bytes = bytes.clone();
        }
    }

    public WechatIncomingImage(ImageSourceType sourceType, byte[] bytes) {
        this(sourceType, null, bytes, null, null, null, null, null);
    }

    public WechatIncomingImage(ImageSourceType sourceType, String sourceReference) {
        this(sourceType, sourceReference, null, null, null, null, null, null);
    }

    public WechatIncomingImage withMetadata(
            String mimeType,
            String fileName,
            Integer width,
            Integer height,
            String colorMode) {
        return new WechatIncomingImage(
                sourceType,
                sourceReference,
                bytes,
                mimeType,
                fileName,
                width,
                height,
                colorMode);
    }

    public boolean hasBytes() {
        return bytes != null && bytes.length > 0;
    }
}

