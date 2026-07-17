package com.example.spring.wechat.client;

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
