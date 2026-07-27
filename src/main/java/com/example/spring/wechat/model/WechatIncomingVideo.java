package com.example.spring.wechat.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record WechatIncomingVideo(
        VideoSourceType sourceType,
        String sourceReference,
        byte[] bytes,
        String mimeType,
        String fileName,
        Long size,
        Integer durationMs,
        String md5,
        String sha256,
        String localPath) {

    public WechatIncomingVideo {
        sourceType = sourceType == null ? VideoSourceType.WECHAT_ATTACHMENT : sourceType;
        sourceReference = safeText(sourceReference);
        bytes = bytes == null ? null : bytes.clone();
        mimeType = safeText(mimeType);
        fileName = fileName == null || fileName.isBlank() ? "wechat-video.mp4" : fileName.strip();
        size = size == null || size < 0 ? bytes == null ? 0L : (long) bytes.length : size;
        durationMs = durationMs == null || durationMs < 0 ? null : durationMs;
        md5 = safeText(md5);
        sha256 = sha256 == null || sha256.isBlank() ? sha256(bytes) : sha256.strip();
        localPath = safeText(localPath);
    }

    public boolean hasBytes() {
        return bytes != null && bytes.length > 0;
    }

    @Override
    public byte[] bytes() {
        return bytes == null ? null : bytes.clone();
    }

    public boolean hasSourceUrl() {
        return sourceReference != null
                && (sourceReference.startsWith("http://") || sourceReference.startsWith("https://"));
    }

    public String sourceUrl() {
        return hasSourceUrl() ? sourceReference : null;
    }

    public boolean hasLocalPath() {
        return localPath != null && !localPath.isBlank();
    }

    public WechatIncomingVideo withLocalPath(String value) {
        return new WechatIncomingVideo(
                sourceType,
                sourceReference,
                bytes,
                mimeType,
                fileName,
                size,
                durationMs,
                md5,
                sha256,
                value);
    }

    public WechatIncomingVideo withBytes(byte[] value) {
        return new WechatIncomingVideo(
                sourceType,
                sourceReference,
                value,
                mimeType,
                fileName,
                size,
                durationMs,
                md5,
                sha256,
                localPath);
    }

    public WechatIncomingVideo withMetadata(
            String mimeType,
            String fileName,
            Long size,
            Integer durationMs,
            String md5,
            String sha256) {
        return new WechatIncomingVideo(
                sourceType,
                sourceReference,
                bytes,
                mimeType,
                fileName,
                size,
                durationMs,
                md5,
                sha256,
                localPath);
    }

    private static String safeText(String value) {
        return value == null ? "" : value.strip();
    }

    private static String sha256(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
