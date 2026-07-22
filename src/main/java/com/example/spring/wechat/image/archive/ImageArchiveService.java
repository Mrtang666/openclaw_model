package com.example.spring.wechat.image.archive;

import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.model.ImageSourceType;
import com.example.spring.wechat.model.WechatIncomingImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 微信图片归档服务。
 *
 * <p>核心职责是把用户上传图片和 AI 生成图片统一保存成“会话图片资源”：
 * 图片字节落到本地磁盘，MySQL 保存可查询的元数据；没有数据库的单元测试环境
 * 会自动退回到内存元数据，保证主流程仍然能运行。</p>
 */
@Component
public class ImageArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ImageArchiveService.class);
    private static final int DEFAULT_BATCH_SIZE = 5;
    private static final int DEFAULT_AVAILABLE_IMAGE_LIMIT = 20;

    private final JdbcTemplate jdbcTemplate;
    private final Path storageRoot;
    private final int batchSize;
    private final List<ArchivedWechatImage> inMemoryImages = new CopyOnWriteArrayList<>();
    private final AtomicLong inMemoryId = new AtomicLong(1L);

    public ImageArchiveService() {
        this(null, Path.of("data", "wechat", "images"), DEFAULT_BATCH_SIZE);
    }

    @Autowired
    public ImageArchiveService(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            @Value("${wechat.image.storage-dir:data/wechat/images}") String storageRoot,
            @Value("${wechat.image.batch-size:5}") int batchSize) {
        this(
                jdbcTemplateProvider == null ? null : jdbcTemplateProvider.getIfAvailable(),
                Path.of(storageRoot),
                batchSize);
    }

    public ImageArchiveService(JdbcTemplate jdbcTemplate, Path storageRoot, int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageRoot = storageRoot == null ? Path.of("data", "wechat", "images") : storageRoot;
        this.batchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
    }

    /**
     * 归档用户一次微信消息中携带的全部图片。
     */
    public List<ArchivedWechatImage> archiveUserImages(
            String wechatUserId,
            String messageId,
            List<WechatIncomingImage> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }

        List<ArchivedWechatImage> archived = new ArrayList<>();
        int startIndex = nextImageIndex(wechatUserId);
        for (int i = 0; i < images.size(); i++) {
            WechatIncomingImage image = images.get(i);
            if (image == null) {
                continue;
            }
            archived.add(saveImage(
                    wechatUserId,
                    messageId,
                    ImageArchiveSourceType.USER_UPLOAD,
                    image.sourceReference(),
                    startIndex + i,
                    image.fileName(),
                    image.mimeType(),
                    image.bytes(),
                    "",
                    "",
                    image.sourceReference()));
        }
        return archived;
    }

    /**
     * 归档系统刚刚生成并发送给用户的图片，后续用户说“修改刚才那张图”时可以找到它。
     */
    public ArchivedWechatImage archiveGeneratedImage(String wechatUserId, ImageGenerationResult image) {
        if (image == null) {
            return null;
        }
        return saveImage(
                wechatUserId,
                "",
                ImageArchiveSourceType.AI_GENERATED,
                image.imageUrl(),
                nextImageIndex(wechatUserId),
                image.fileName(),
                image.contentType(),
                image.imageBytes(),
                image.imageUrl(),
                image.prompt(),
                image.imageUrl());
    }

    public List<ArchivedWechatImage> pendingImages(String wechatUserId) {
        return findImages(wechatUserId, "PENDING", null);
    }

    public List<WechatIncomingImage> pendingWechatImages(String wechatUserId) {
        return pendingImages(wechatUserId).stream()
                .map(this::toWechatIncomingImage)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    /**
     * 查询当前会话仍可被再次引用的图片资源。
     *
     * <p>这里不只返回 PENDING 图片，也会返回已经 USED 的最近图片。
     * PENDING 只表示“用户刚发来但还没明确说要怎么处理”，USED 表示已经被处理过一次，
     * 但图片文件仍然保存在本地，可以继续用于“再分析一下刚才那张图”“把这三张图放进 PDF”这类后续需求。</p>
     */
    public List<ArchivedWechatImage> availableImages(String wechatUserId) {
        List<ArchivedWechatImage> images = findImages(wechatUserId, null, null).stream()
                .filter(image -> !"FAILED".equalsIgnoreCase(image.status()))
                .filter(image -> !"ARCHIVED".equalsIgnoreCase(image.status()))
                .toList();
        if (images.size() <= DEFAULT_AVAILABLE_IMAGE_LIMIT) {
            return images;
        }
        return images.subList(images.size() - DEFAULT_AVAILABLE_IMAGE_LIMIT, images.size());
    }

    public List<WechatIncomingImage> availableWechatImages(String wechatUserId) {
        return availableImages(wechatUserId).stream()
                .map(this::toWechatIncomingImage)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public Optional<WechatIncomingImage> latestGeneratedWechatImage(String wechatUserId) {
        return findImages(wechatUserId, null, ImageArchiveSourceType.AI_GENERATED).stream()
                .max(Comparator.comparing(ArchivedWechatImage::createdAt))
                .flatMap(this::toWechatIncomingImage);
    }

    public void markUsed(String wechatUserId, List<ArchivedWechatImage> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        for (ArchivedWechatImage image : images) {
            if (image == null) {
                continue;
            }
            if (jdbcTemplate != null && image.id() != null) {
                try {
                    jdbcTemplate.update(
                            "UPDATE wechat_images SET status = ?, used_at = ? WHERE id = ? AND wechat_user_id = ?",
                            "USED",
                            now,
                            image.id(),
                            safeText(wechatUserId));
                } catch (RuntimeException exception) {
                    log.warn("微信图片资源状态更新失败，userId={}, imageId={}, error={}",
                            wechatUserId, image.id(), rootMessage(exception));
                }
            }
            inMemoryImages.removeIf(item -> item.id() != null && item.id().equals(image.id()));
            inMemoryImages.add(new ArchivedWechatImage(
                    image.id(),
                    image.wechatUserId(),
                    image.messageId(),
                    image.sourceType(),
                    image.sourceReference(),
                    image.imageIndex(),
                    image.fileName(),
                    image.mimeType(),
                    image.sha256(),
                    image.md5(),
                    image.sizeBytes(),
                    image.localPath(),
                    image.imageUrl(),
                    image.prompt(),
                    image.description(),
                    "USED",
                    image.createdAt(),
                    Instant.now()));
        }
    }

    public List<WechatIncomingImage> toWechatImages(List<ArchivedWechatImage> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.stream()
                .map(this::toWechatIncomingImage)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public ImageArchiveCleanupResult cleanExpiredImages(Instant now, int retentionDays) {
        Instant time = now == null ? Instant.now() : now;
        Instant cutoff = retentionDays <= 0 ? time : time.minusSeconds(retentionDays * 86_400L);
        ImageArchiveCleanupResult databaseResult = cleanExpiredDatabaseImages(cutoff);
        ImageArchiveCleanupResult memoryResult = cleanExpiredInMemoryImages(cutoff);
        return databaseResult.plus(memoryResult);
    }

    public List<List<WechatIncomingImage>> batches(List<WechatIncomingImage> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<List<WechatIncomingImage>> batches = new ArrayList<>();
        for (int start = 0; start < images.size(); start += batchSize) {
            int end = Math.min(start + batchSize, images.size());
            batches.add(List.copyOf(images.subList(start, end)));
        }
        return batches;
    }

    public String pendingImageContext(String wechatUserId) {
        List<ArchivedWechatImage> pending = pendingImages(wechatUserId);
        if (pending.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder("当前待处理图片：共 ")
                .append(pending.size())
                .append(" 张；一次最多处理 ")
                .append(batchSize)
                .append(" 张，超过后会自动分批。");
        for (ArchivedWechatImage image : pending.stream().limit(5).toList()) {
            context.append('\n')
                    .append("- 第")
                    .append(image.imageIndex())
                    .append("张：")
                    .append(image.sourceType() == ImageArchiveSourceType.AI_GENERATED ? "AI生成图片" : "用户上传图片")
                    .append("，文件名：")
                    .append(image.fileName());
        }
        return context.toString();
    }

    public String imageResourceContext(String wechatUserId) {
        List<ArchivedWechatImage> images = availableImages(wechatUserId);
        if (images.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder("当前可用图片资源：共 ")
                .append(images.size())
                .append(" 张；一次最多处理 ")
                .append(batchSize)
                .append(" 张，超过后会自动分批。");
        for (ArchivedWechatImage image : images.stream().limit(10).toList()) {
            context.append('\n')
                    .append("- 第")
                    .append(image.imageIndex())
                    .append("张：")
                    .append(image.sourceType() == ImageArchiveSourceType.AI_GENERATED ? "AI生成图片" : "用户上传图片")
                    .append("，状态：")
                    .append("PENDING".equalsIgnoreCase(image.status()) ? "待处理" : "已处理，可再次引用")
                    .append("，文件名：")
                    .append(image.fileName());
        }
        return context.toString();
    }

    private ArchivedWechatImage saveImage(
            String wechatUserId,
            String messageId,
            ImageArchiveSourceType sourceType,
            String sourceReference,
            int imageIndex,
            String fileName,
            String mimeType,
            byte[] bytes,
            String imageUrl,
            String prompt,
            String fallbackReference) {
        byte[] safeBytes = bytes == null ? new byte[0] : bytes.clone();
        String sha256 = safeBytes.length == 0 ? "" : digest("SHA-256", safeBytes);
        String md5 = safeBytes.length == 0 ? "" : digest("MD5", safeBytes);
        String normalizedFileName = fileName == null || fileName.isBlank()
                ? defaultFileName(sourceType, mimeType)
                : fileName.strip();
        String localPath = saveBytes(wechatUserId, sha256, normalizedFileName, safeBytes);
        ArchivedWechatImage image = new ArchivedWechatImage(
                null,
                safeText(wechatUserId),
                safeText(messageId),
                sourceType,
                safeText(sourceReference),
                imageIndex,
                normalizedFileName,
                mimeType,
                sha256,
                md5,
                safeBytes.length,
                localPath,
                safeText(imageUrl),
                safeText(prompt),
                "",
                "PENDING",
                Instant.now(),
                null);
        ArchivedWechatImage saved = insertMetadata(image);
        if (saved.localPath().isBlank() && fallbackReference != null && !fallbackReference.isBlank()) {
            return saved;
        }
        return saved;
    }

    private ArchivedWechatImage insertMetadata(ArchivedWechatImage image) {
        Long id = null;
        if (jdbcTemplate != null) {
            try {
                KeyHolder keyHolder = new GeneratedKeyHolder();
                jdbcTemplate.update(connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            """
                                    INSERT INTO wechat_images
                                    (wechat_user_id, message_id, source_type, source_reference, image_index,
                                     file_name, mime_type, sha256, md5, size_bytes, local_path, image_url,
                                     prompt, description, status, created_at, used_at)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                            Statement.RETURN_GENERATED_KEYS);
                    statement.setString(1, image.wechatUserId());
                    statement.setString(2, image.messageId());
                    statement.setString(3, image.sourceType().name());
                    statement.setString(4, image.sourceReference());
                    statement.setInt(5, image.imageIndex());
                    statement.setString(6, image.fileName());
                    statement.setString(7, image.mimeType());
                    statement.setString(8, image.sha256());
                    statement.setString(9, image.md5());
                    statement.setLong(10, image.sizeBytes());
                    statement.setString(11, image.localPath());
                    statement.setString(12, image.imageUrl());
                    statement.setString(13, image.prompt());
                    statement.setString(14, image.description());
                    statement.setString(15, image.status());
                    statement.setTimestamp(16, Timestamp.from(image.createdAt()));
                    statement.setTimestamp(17, image.usedAt() == null ? null : Timestamp.from(image.usedAt()));
                    return statement;
                }, keyHolder);
                Number key = keyHolder.getKey();
                if (key != null) {
                    id = key.longValue();
                }
            } catch (RuntimeException exception) {
                log.warn("微信图片元数据写入 MySQL 失败，userId={}, fileName={}, error={}",
                        image.wechatUserId(), image.fileName(), rootMessage(exception));
            }
        }
        if (id == null) {
            id = inMemoryId.getAndIncrement();
        }
        ArchivedWechatImage saved = new ArchivedWechatImage(
                id,
                image.wechatUserId(),
                image.messageId(),
                image.sourceType(),
                image.sourceReference(),
                image.imageIndex(),
                image.fileName(),
                image.mimeType(),
                image.sha256(),
                image.md5(),
                image.sizeBytes(),
                image.localPath(),
                image.imageUrl(),
                image.prompt(),
                image.description(),
                image.status(),
                image.createdAt(),
                image.usedAt());
        inMemoryImages.add(saved);
        return saved;
    }

    private List<ArchivedWechatImage> findImages(
            String wechatUserId,
            String status,
            ImageArchiveSourceType sourceType) {
        if (jdbcTemplate != null) {
            try {
                StringBuilder sql = new StringBuilder(
                        """
                                SELECT id, wechat_user_id, message_id, source_type, source_reference, image_index,
                                       file_name, mime_type, sha256, md5, size_bytes, local_path, image_url,
                                       prompt, description, status, created_at, used_at
                                FROM wechat_images
                                WHERE wechat_user_id = ?
                                """);
                List<Object> args = new ArrayList<>();
                args.add(safeText(wechatUserId));
                if (status != null && !status.isBlank()) {
                    sql.append(" AND status = ?");
                    args.add(status);
                }
                if (sourceType != null) {
                    sql.append(" AND source_type = ?");
                    args.add(sourceType.name());
                }
                sql.append(" ORDER BY created_at ASC, image_index ASC");
                return jdbcTemplate.query(sql.toString(), imageRowMapper(), args.toArray());
            } catch (RuntimeException exception) {
                log.warn("微信图片元数据查询 MySQL 失败，userId={}, error={}", wechatUserId, rootMessage(exception));
            }
        }
        return inMemoryImages.stream()
                .filter(image -> image.wechatUserId().equals(safeText(wechatUserId)))
                .filter(image -> status == null || status.isBlank() || status.equals(image.status()))
                .filter(image -> sourceType == null || sourceType == image.sourceType())
                .sorted(Comparator.comparing(ArchivedWechatImage::createdAt).thenComparing(ArchivedWechatImage::imageIndex))
                .toList();
    }

    private ImageArchiveCleanupResult cleanExpiredDatabaseImages(Instant cutoff) {
        if (jdbcTemplate == null) {
            return new ImageArchiveCleanupResult(0, 0);
        }
        try {
            List<ArchivedWechatImage> expired = jdbcTemplate.query(
                    """
                            SELECT id, wechat_user_id, message_id, source_type, source_reference, image_index,
                                   file_name, mime_type, sha256, md5, size_bytes, local_path, image_url,
                                   prompt, description, status, created_at, used_at
                            FROM wechat_images
                            WHERE created_at < ?
                            """,
                    imageRowMapper(),
                    Timestamp.from(cutoff));
            if (expired.isEmpty()) {
                return new ImageArchiveCleanupResult(0, 0);
            }
            int deletedFiles = deleteLocalFiles(expired);
            int deletedRows = jdbcTemplate.update(
                    "DELETE FROM wechat_images WHERE created_at < ?",
                    Timestamp.from(cutoff));
            inMemoryImages.removeIf(image -> image.createdAt().isBefore(cutoff));
            return new ImageArchiveCleanupResult(deletedRows, deletedFiles);
        } catch (RuntimeException exception) {
            log.warn("微信图片过期资源清理失败，error={}", rootMessage(exception));
            return new ImageArchiveCleanupResult(0, 0);
        }
    }

    private ImageArchiveCleanupResult cleanExpiredInMemoryImages(Instant cutoff) {
        List<ArchivedWechatImage> expired = inMemoryImages.stream()
                .filter(image -> image.createdAt().isBefore(cutoff))
                .toList();
        if (expired.isEmpty()) {
            return new ImageArchiveCleanupResult(0, 0);
        }
        int deletedFiles = deleteLocalFiles(expired);
        inMemoryImages.removeIf(image -> image.createdAt().isBefore(cutoff));
        return new ImageArchiveCleanupResult(expired.size(), deletedFiles);
    }

    private int deleteLocalFiles(List<ArchivedWechatImage> images) {
        int deleted = 0;
        for (ArchivedWechatImage image : images) {
            if (image == null || image.localPath().isBlank()) {
                continue;
            }
            try {
                if (Files.deleteIfExists(Path.of(image.localPath()))) {
                    deleted++;
                }
            } catch (Exception exception) {
                log.warn("删除微信图片本地文件失败，path={}, error={}",
                        image.localPath(), rootMessage(exception));
            }
        }
        return deleted;
    }

    private Optional<WechatIncomingImage> toWechatIncomingImage(ArchivedWechatImage image) {
        if (image == null) {
            return Optional.empty();
        }
        byte[] bytes = readBytes(image.localPath());
        if ((bytes == null || bytes.length == 0) && !image.imageUrl().isBlank()) {
            return Optional.of(new WechatIncomingImage(
                    ImageSourceType.TEXT_URL,
                    image.imageUrl(),
                    null,
                    image.mimeType(),
                    image.fileName(),
                    null,
                    null,
                    null));
        }
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        return Optional.of(new WechatIncomingImage(
                ImageSourceType.WECHAT_ATTACHMENT,
                image.sourceReference(),
                bytes,
                image.mimeType(),
                image.fileName(),
                null,
                null,
                null));
    }

    private byte[] readBytes(String localPath) {
        if (localPath == null || localPath.isBlank()) {
            return new byte[0];
        }
        try {
            return Files.readAllBytes(Path.of(localPath));
        } catch (Exception exception) {
            log.warn("读取本地图片失败，path={}, error={}", localPath, rootMessage(exception));
            return new byte[0];
        }
    }

    private String saveBytes(String wechatUserId, String sha256, String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        try {
            LocalDate today = LocalDate.now();
            Path dir = storageRoot
                    .resolve(String.valueOf(today.getYear()))
                    .resolve("%02d".formatted(today.getMonthValue()))
                    .resolve("%02d".formatted(today.getDayOfMonth()))
                    .resolve(sanitizePathPart(wechatUserId))
                    .resolve(sha256 == null || sha256.isBlank() ? "no-hash" : sha256);
            Files.createDirectories(dir);
            Path target = dir.resolve(sanitizeFileName(fileName));
            Files.write(target, bytes);
            return target.toAbsolutePath().toString();
        } catch (Exception exception) {
            log.warn("保存微信图片文件失败，userId={}, fileName={}, error={}",
                    wechatUserId, fileName, rootMessage(exception));
            return "";
        }
    }

    private int nextImageIndex(String wechatUserId) {
        return findImages(wechatUserId, null, null).stream()
                .mapToInt(ArchivedWechatImage::imageIndex)
                .max()
                .orElse(0) + 1;
    }

    private RowMapper<ArchivedWechatImage> imageRowMapper() {
        return (rs, rowNum) -> new ArchivedWechatImage(
                rs.getLong("id"),
                rs.getString("wechat_user_id"),
                rs.getString("message_id"),
                parseSourceType(rs.getString("source_type")),
                rs.getString("source_reference"),
                rs.getInt("image_index"),
                rs.getString("file_name"),
                rs.getString("mime_type"),
                rs.getString("sha256"),
                rs.getString("md5"),
                rs.getLong("size_bytes"),
                rs.getString("local_path"),
                rs.getString("image_url"),
                rs.getString("prompt"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getTimestamp("created_at") == null ? Instant.now() : rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant());
    }

    private ImageArchiveSourceType parseSourceType(String value) {
        try {
            return ImageArchiveSourceType.valueOf(value);
        } catch (Exception ignored) {
            return ImageArchiveSourceType.USER_UPLOAD;
        }
    }

    private String digest(String algorithm, byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
        } catch (Exception exception) {
            return "";
        }
    }

    private String defaultFileName(ImageArchiveSourceType sourceType, String mimeType) {
        String prefix = sourceType == ImageArchiveSourceType.AI_GENERATED ? "generated-image" : "wechat-image";
        return prefix + "." + extension(mimeType);
    }

    private String extension(String mimeType) {
        String value = mimeType == null ? "" : mimeType.toLowerCase();
        if (value.contains("jpeg") || value.contains("jpg")) {
            return "jpg";
        }
        if (value.contains("webp")) {
            return "webp";
        }
        if (value.contains("gif")) {
            return "gif";
        }
        return "png";
    }

    private String sanitizePathPart(String value) {
        String text = value == null || value.isBlank() ? "unknown" : value.strip();
        return text.replaceAll("[\\\\/:*?\"<>|@\\s]+", "_");
    }

    private String sanitizeFileName(String value) {
        String text = value == null || value.isBlank() ? "wechat-image.png" : value.strip();
        return text.replaceAll("[\\\\/:*?\"<>|]+", "_");
    }

    private String safeText(String value) {
        return value == null ? "" : value.strip();
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
