package com.example.spring.wechat.email.model;

import java.nio.file.Path;

public record PreparedEmailAttachment(
        Path path,
        String fileName,
        long sizeBytes,
        boolean generatedZip) {
}
