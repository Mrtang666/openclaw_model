package com.example.spring.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

@Service
public class DocumentMemoryService implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(DocumentMemoryService.class);
    private static final Pattern REFERENCE = Pattern.compile(
        "(文件|文档|PDF|Word|Excel|PPT|表格|附件|上一个|刚才|之前|该文件|这个文件|第[一二三四五六七八九十1-9]个)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDINAL = Pattern.compile("第([一二三四五六七八九十1-9])个");
    private static final Pattern ACTIVE_FOLLOW_UP = Pattern.compile(
        "(?i)(^[1-5一二三四五]$|总结|摘要|重点|分析|回答|问题|整理|输出|导出|转换|转成|"
            + "Word|PDF|它|内容|主要讲|继续)");

    private final DocumentProperties properties;
    private final DocumentExtractor extractor;
    private final Object monitor = new Object();
    private final ConcurrentHashMap<String, Long> activeSessions = new ConcurrentHashMap<>();
    private Path databasePath;
    private Path documentDirectory;

    public DocumentMemoryService(
        DocumentProperties properties,
        DocumentExtractor extractor) {
        this.properties = properties;
        this.extractor = extractor;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!properties.isEnabled()) {
            return;
        }
        Path root = properties.getDataDirectory().toAbsolutePath().normalize();
        databasePath = root.resolve("memory.db");
        documentDirectory = root.resolve("documents");
        Files.createDirectories(documentDirectory);
        initializeSchema();
        pruneExpired();
    }

    public DocumentAsset store(
        String userId,
        Long messageId,
        byte[] data,
        String originalFileName) throws IOException {
        if (!properties.isEnabled()) {
            throw new IOException("文件功能已禁用");
        }
        String fileName = DocumentExtractor.safeFileName(originalFileName);
        DocumentExtractor.ExtractionResult extraction = extractor.extract(data, fileName);
        String documentId = UUID.randomUUID().toString();
        Path userDirectory = documentDirectory.resolve(hashUserId(userId));
        Files.createDirectories(userDirectory);
        Path path = userDirectory.resolve(documentId + extension(fileName));
        Files.write(path, data, StandardOpenOption.CREATE_NEW);
        String description = abbreviate(extraction.text(), 240);
        String sql = """
            INSERT INTO memory_documents(
                document_id, user_id, message_id, source, file_name, media_type,
                file_path, extracted_text, description, size_bytes, sha256, created_at)
            VALUES (?, ?, ?, 'USER', ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try {
            synchronized (monitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, documentId);
                    statement.setString(2, userId);
                    if (messageId == null) statement.setNull(3, java.sql.Types.BIGINT);
                    else statement.setLong(3, messageId);
                    statement.setString(4, fileName);
                    statement.setString(5, extraction.mediaType());
                    statement.setString(6, path.toString());
                    statement.setString(7, extraction.text());
                    statement.setString(8, description);
                    statement.setLong(9, data.length);
                    statement.setString(10, sha256(data));
                    statement.setLong(11, Instant.now().toEpochMilli());
                    statement.executeUpdate();
                }
                pruneUser(userId);
            }
        } catch (SQLException exception) {
            Files.deleteIfExists(path);
            throw new IOException("保存文件记忆失败", exception);
        }
        activeSessions.put(userId, Instant.now().plusSeconds(30 * 60).toEpochMilli());
        return new DocumentAsset(documentId, fileName, extraction.mediaType(),
            data, extraction.text(), description);
    }

    public List<DocumentAsset> resolve(String userId, String userText) {
        if (!properties.isEnabled() || userId == null || userId.isBlank()) {
            return List.of();
        }
        String text = userText == null ? "" : userText.trim();
        boolean explicit = REFERENCE.matcher(text).find();
        boolean activeFollowUp = activeSessions.getOrDefault(userId, 0L)
            >= Instant.now().toEpochMilli() && ACTIVE_FOLLOW_UP.matcher(text).find();
        if (!explicit && !activeFollowUp) {
            return List.of();
        }
        List<DocumentAsset> recent = loadRecent(userId);
        if (recent.isEmpty()) {
            return List.of();
        }
        activeSessions.put(userId, Instant.now().plusSeconds(30 * 60).toEpochMilli());
        Matcher ordinal = ORDINAL.matcher(userText == null ? "" : userText);
        if (ordinal.find()) {
            Collections.reverse(recent);
            int index = Math.min(recent.size() - 1,
                Math.max(0, ordinalValue(ordinal.group(1)) - 1));
            return List.of(recent.get(index));
        }
        if ((userText != null && (userText.contains("两个文件") || userText.contains("两个文档")
            || userText.contains("比较"))) && recent.size() > 1) {
            return List.copyOf(recent.subList(0, Math.min(2, recent.size())));
        }
        return List.of(recent.get(0));
    }

    public List<DocumentAsset> findByMessage(String userId, Long messageId) {
        if (!properties.isEnabled() || userId == null || userId.isBlank() || messageId == null) {
            return List.of();
        }
        String sql = """
            SELECT document_id, file_name, media_type, file_path, extracted_text, description
            FROM memory_documents WHERE user_id = ? AND message_id = ? ORDER BY created_at
            """;
        List<DocumentAsset> result = new ArrayList<>();
        try {
            synchronized (monitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, userId);
                    statement.setLong(2, messageId);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            Path path = Path.of(rows.getString("file_path"));
                            if (Files.isRegularFile(path)) {
                                result.add(new DocumentAsset(rows.getString("document_id"),
                                    rows.getString("file_name"), rows.getString("media_type"),
                                    Files.readAllBytes(path), rows.getString("extracted_text"),
                                    rows.getString("description")));
                            }
                        }
                    }
                }
            }
        } catch (SQLException | IOException exception) {
            log.warn("按消息读取文件记忆失败，userId={}，messageId={}",
                userId, messageId, exception);
        }
        return result;
    }

    private List<DocumentAsset> loadRecent(String userId) {
        String sql = """
            SELECT document_id, file_name, media_type, file_path, extracted_text, description
            FROM memory_documents WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
            """;
        List<DocumentAsset> result = new ArrayList<>();
        try {
            synchronized (monitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, userId);
                    statement.setInt(2, Math.max(1, properties.getMaxFilesPerUser()));
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            Path path = Path.of(rows.getString("file_path"));
                            if (Files.isRegularFile(path)) {
                                result.add(new DocumentAsset(
                                    rows.getString("document_id"),
                                    rows.getString("file_name"),
                                    rows.getString("media_type"),
                                    Files.readAllBytes(path),
                                    rows.getString("extracted_text"),
                                    rows.getString("description")));
                            }
                        }
                    }
                }
            }
        } catch (SQLException | IOException exception) {
            log.warn("读取文件记忆失败，userId={}", userId, exception);
        }
        return result;
    }

    private void initializeSchema() throws SQLException {
        synchronized (monitor) {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS memory_documents (
                        document_id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        message_id INTEGER,
                        source TEXT NOT NULL,
                        file_name TEXT NOT NULL,
                        media_type TEXT,
                        file_path TEXT NOT NULL,
                        extracted_text TEXT,
                        description TEXT,
                        size_bytes INTEGER NOT NULL,
                        sha256 TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
                statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_memory_documents_user
                    ON memory_documents(user_id, created_at DESC)
                    """);
            }
        }
    }

    private void pruneExpired() {
        long retention = properties.getRetention() == null
            ? java.time.Duration.ofDays(7).toMillis()
            : Math.max(0, properties.getRetention().toMillis());
        if (retention == 0) return;
        String sql = "SELECT document_id, file_path FROM memory_documents WHERE created_at < ?";
        try {
            synchronized (monitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, Instant.now().toEpochMilli() - retention);
                    deleteRows(connection, statement);
                }
            }
        } catch (SQLException | IOException exception) {
            log.warn("清理过期文件记忆失败", exception);
        }
    }

    private void pruneUser(String userId) throws SQLException, IOException {
        String sql = """
            SELECT document_id, file_path, size_bytes FROM memory_documents
            WHERE user_id = ? ORDER BY created_at DESC
            """;
        List<Row> rows = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new Row(result.getString(1), Path.of(result.getString(2)), result.getLong(3)));
                }
            }
            long bytes = 0;
            int count = 0;
            for (Row row : rows) {
                if (count < properties.getMaxFilesPerUser()
                    && bytes + row.bytes() <= properties.getMaxBytesPerUser()) {
                    count++;
                    bytes += row.bytes();
                } else {
                    Files.deleteIfExists(row.path());
                    try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM memory_documents WHERE document_id = ?")) {
                        delete.setString(1, row.id());
                        delete.executeUpdate();
                    }
                }
            }
        }
    }

    private static void deleteRows(Connection connection, PreparedStatement select)
        throws SQLException, IOException {
        List<Row> rows = new ArrayList<>();
        try (ResultSet result = select.executeQuery()) {
            while (result.next()) rows.add(new Row(result.getString(1), Path.of(result.getString(2)), 0));
        }
        for (Row row : rows) {
            Files.deleteIfExists(row.path());
            try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM memory_documents WHERE document_id = ?")) {
                delete.setString(1, row.id());
                delete.executeUpdate();
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && fileName.length() - dot <= 8 ? fileName.substring(dot) : ".bin";
    }

    private static String hashUserId(String userId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(userId.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 0, 12);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private static int ordinalValue(String value) {
        return switch (value) {
            case "一" -> 1; case "二" -> 2; case "三" -> 3; case "四" -> 4;
            case "五" -> 5; case "六" -> 6; case "七" -> 7; case "八" -> 8;
            case "九" -> 9; case "十" -> 10; default -> Integer.parseInt(value);
        };
    }

    private record Row(String id, Path path, long bytes) { }
}
