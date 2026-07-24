package com.example.spring.wechat.web.model;

import java.time.Instant;

/**
 * 网页正文提取结果。
 */
public record WebPageContent(
        String url,
        String title,
        String content,
        Instant fetchedAt) {
}
