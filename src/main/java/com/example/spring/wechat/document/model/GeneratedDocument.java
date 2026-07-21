package com.example.spring.wechat.document.model;

/**
 * 文档生成工具返回给微信发送层的文件结果。
 */
public record GeneratedDocument(
        byte[] bytes,
        String fileName,
        DocumentFormat format,
        String contentType,
        String caption) {

    public GeneratedDocument {
        bytes = bytes == null ? new byte[0] : bytes.clone();
        format = format == null ? DocumentFormat.UNKNOWN : format;
        fileName = fileName == null || fileName.isBlank() ? "document." + format.extension() : fileName.strip();
        contentType = contentType == null || contentType.isBlank() ? format.contentType() : contentType.strip();
        caption = caption == null ? "" : caption.strip();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
