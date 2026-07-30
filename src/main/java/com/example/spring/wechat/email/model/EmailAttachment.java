package com.example.spring.wechat.email.model;

public record EmailAttachment(byte[] bytes, String fileName, String contentType) {

    public EmailAttachment {
        bytes = bytes == null ? new byte[0] : bytes.clone();
        fileName = fileName == null || fileName.isBlank() ? "attachment.bin" : fileName.strip();
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType.strip();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
