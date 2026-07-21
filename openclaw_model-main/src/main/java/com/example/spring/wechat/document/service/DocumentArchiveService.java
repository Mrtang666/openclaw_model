package com.example.spring.wechat.document.service;

import com.example.spring.wechat.document.model.DocumentChunk;
import com.example.spring.wechat.document.model.ParsedDocument;
import com.example.spring.wechat.model.WechatIncomingFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 文档归档服务：负责保存原始文件，并在 MySQL 可用时写入元数据和分块。
 */
@Component
public class DocumentArchiveService {

    private static final Logger log = LoggerFactory.getLogger(DocumentArchiveService.class);

    private final JdbcTemplate jdbcTemplate;
    private final Path storageRoot;

    public DocumentArchiveService() {
        this(null, Path.of("data", "wechat", "documents"));
    }

    @Autowired
    public DocumentArchiveService(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            @Value("${wechat.document.storage-dir:data/wechat/documents}") String storageRoot) {
        this(jdbcTemplateProvider == null ? null : jdbcTemplateProvider.getIfAvailable(), Path.of(storageRoot));
    }

    private DocumentArchiveService(JdbcTemplate jdbcTemplate, Path storageRoot) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageRoot = storageRoot == null ? Path.of("data", "wechat", "documents") : storageRoot;
    }

    public void archive(String wechatUserId, WechatIncomingFile file, ParsedDocument parsed) {
        if (file == null || parsed == null) {
            return;
        }
        String localPath = saveOriginalFile(wechatUserId, file);
        saveMetadata(wechatUserId, file, parsed, localPath);
    }

    private String saveOriginalFile(String wechatUserId, WechatIncomingFile file) {
        if (!file.hasBytes()) {
            return "";
        }
        try {
            LocalDate today = LocalDate.now();
            String userPart = sanitizePathPart(wechatUserId);
            String hashPart = file.sha256() == null || file.sha256().isBlank() ? "no-hash" : file.sha256();
            Path dir = storageRoot
                    .resolve(String.valueOf(today.getYear()))
                    .resolve("%02d".formatted(today.getMonthValue()))
                    .resolve("%02d".formatted(today.getDayOfMonth()))
                    .resolve(userPart)
                    .resolve(hashPart);
            Files.createDirectories(dir);
            Path target = dir.resolve(sanitizeFileName(file.fileName()));
            Files.write(target, file.bytes());
            return target.toAbsolutePath().toString();
        } catch (Exception exception) {
            log.warn("微信文档原始文件保存失败，userId={}, fileName={}, error={}",
                    wechatUserId, file.fileName(), rootMessage(exception));
            return "";
        }
    }

    private void saveMetadata(String wechatUserId, WechatIncomingFile file, ParsedDocument parsed, String localPath) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            Timestamp now = Timestamp.from(Instant.now());
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                                INSERT INTO wechat_documents
                                (wechat_user_id, source_reference, file_name, mime_type, document_format,
                                 sha256, md5, size_bytes, local_path, summary, created_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, safeText(wechatUserId));
                statement.setString(2, safeText(file.sourceReference()));
                statement.setString(3, safeText(file.fileName()));
                statement.setString(4, safeText(file.mimeType()));
                statement.setString(5, parsed.format().name());
                statement.setString(6, safeText(file.sha256()));
                statement.setString(7, safeText(file.md5()));
                statement.setLong(8, file.size() == null ? 0L : file.size());
                statement.setString(9, safeText(localPath));
                statement.setString(10, safeText(parsed.summary()));
                statement.setTimestamp(11, now);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key != null) {
                saveChunks(key.longValue(), parsed, now);
            }
        } catch (Exception exception) {
            log.warn("微信文档元数据写入 MySQL 失败，userId={}, fileName={}, error={}",
                    wechatUserId, file.fileName(), rootMessage(exception));
        }
    }

    private void saveChunks(long documentId, ParsedDocument parsed, Timestamp now) {
        for (DocumentChunk chunk : parsed.chunks()) {
            jdbcTemplate.update(
                    """
                            INSERT INTO wechat_document_chunks
                            (document_id, chunk_index, title, chunk_text, summary, created_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    documentId,
                    chunk.index(),
                    safeText(chunk.title()),
                    safeText(chunk.text()),
                    safeText(chunk.summary()),
                    now);
        }
    }

    private String sanitizePathPart(String value) {
        String text = value == null || value.isBlank() ? "unknown" : value.strip();
        return text.replaceAll("[\\\\/:*?\"<>|@\\s]+", "_");
    }

    private String sanitizeFileName(String value) {
        String text = value == null || value.isBlank() ? "wechat-file" : value.strip();
        return text.replaceAll("[\\\\/:*?\"<>|]+", "_");
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
