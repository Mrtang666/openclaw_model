package com.example.spring.wechat.email.model;

import java.nio.file.Path;

public record DownloadedEmailAttachment(
        String messageUid,
        String fileName,
        String contentType,
        Path path,
        byte[] bytes,
        long sizeBytes) {

    public DownloadedEmailAttachment {
        bytes = bytes == null ? null : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes == null ? null : bytes.clone();
    }
}
