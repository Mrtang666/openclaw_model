package com.example.spring.wechat.image.archive;

/**
 * 图片资源清理结果。
 *
 * <p>deletedMetadata 表示删除了多少条图片元数据；
 * deletedLocalFiles 表示成功删除了多少个本地图片文件。</p>
 */
public record ImageArchiveCleanupResult(int deletedMetadata, int deletedLocalFiles) {

    public ImageArchiveCleanupResult plus(ImageArchiveCleanupResult other) {
        if (other == null) {
            return this;
        }
        return new ImageArchiveCleanupResult(
                deletedMetadata + other.deletedMetadata,
                deletedLocalFiles + other.deletedLocalFiles);
    }
}
