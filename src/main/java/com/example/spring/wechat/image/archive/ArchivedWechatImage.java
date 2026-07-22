package com.example.spring.wechat.image.archive;

import java.time.Instant;

/**
 * 已归档的微信图片资源。
 *
 * <p>这个对象只保存图片的元数据和本地文件路径，真正的图片字节放在磁盘上，
 * 这样可以避免把大图片直接塞进数据库。</p>
 */
public record ArchivedWechatImage(
        Long id,
        String wechatUserId,
        String messageId,
        ImageArchiveSourceType sourceType,
        String sourceReference,
        int imageIndex,
        String fileName,
        String mimeType,
        String sha256,
        String md5,
        long sizeBytes,
        String localPath,
        String imageUrl,
        String prompt,
        String description,
        String status,
        Instant createdAt,
        Instant usedAt) {

    public ArchivedWechatImage {
        wechatUserId = safe(wechatUserId);
        messageId = safe(messageId);
        sourceType = sourceType == null ? ImageArchiveSourceType.USER_UPLOAD : sourceType;
        sourceReference = safe(sourceReference);
        fileName = fileName == null || fileName.isBlank() ? "wechat-image.png" : fileName.strip();
        mimeType = mimeType == null || mimeType.isBlank() ? "image/png" : mimeType.strip();
        sha256 = safe(sha256);
        md5 = safe(md5);
        localPath = safe(localPath);
        imageUrl = safe(imageUrl);
        prompt = safe(prompt);
        description = safe(description);
        status = status == null || status.isBlank() ? "PENDING" : status.strip();
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
