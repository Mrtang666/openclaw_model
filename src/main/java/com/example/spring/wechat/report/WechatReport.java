package com.example.spring.wechat.report;

import java.nio.file.Path;
import java.time.Instant;

public record WechatReport(
        String id,
        String title,
        String url,
        Path path,
        Instant createdAt,
        Instant expiresAt) {
}
