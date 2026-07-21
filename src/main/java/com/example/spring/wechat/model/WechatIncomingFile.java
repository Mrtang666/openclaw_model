package com.example.spring.wechat.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 微信端收到的普通文件附件。
 *
 * <p>它和图片、语音一样属于用户输入的一种信息类型。这里保存文件名、MIME 类型、
 * 二进制内容和哈希值，方便后续文档解析工具识别文件、去重和写入上下文。</p>
 */
public record WechatIncomingFile(
        String sourceReference,
        String fileName,
        String mimeType,
        byte[] bytes,
        Long size,
        String md5,
        String sha256) {

    public WechatIncomingFile {
        sourceReference = safeText(sourceReference);
        fileName = fileName == null || fileName.isBlank() ? "wechat-file" : fileName.strip();
        mimeType = safeText(mimeType);
        bytes = bytes == null ? null : bytes.clone();
        size = size == null || size < 0 ? bytes == null ? 0L : (long) bytes.length : size;
        md5 = safeText(md5);
        sha256 = sha256 == null || sha256.isBlank() ? sha256(bytes) : sha256.strip();
    }

    @Override
    public byte[] bytes() {
        return bytes == null ? null : bytes.clone();
    }

    public boolean hasBytes() {
        return bytes != null && bytes.length > 0;
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
