package com.example.spring.wechat.web.context;

import java.time.Instant;

/**
 * 一次网页阅读的轻量快照。
 *
 * <p>这里不保存网页全文，只保存后续上下文引用需要的标题、URL 和短摘要。</p>
 */
public record WebReadSnapshot(
        String title,
        String url,
        String summary,
        Instant readAt) {

    public WebReadSnapshot {
        title = title == null ? "" : title.strip();
        url = url == null ? "" : url.strip();
        summary = summary == null ? "" : summary.strip();
        readAt = readAt == null ? Instant.now() : readAt;
    }
}
