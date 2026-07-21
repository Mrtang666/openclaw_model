package com.example.spring.memory;

import com.example.spring.agent.AgentRequest;
import com.example.spring.agent.AgentResponse;
import com.example.spring.agent.AgentType;
import com.example.spring.agent.ImageAsset;
import com.example.spring.speech.voice.VoicePreference;
import com.example.spring.wechat.ReplyMode;
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
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

@Service
public class ConversationMemoryService implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);
    private static final Pattern IMAGE_REFERENCE = Pattern.compile(
        "(刚才|上一张|上张|之前|那张|这张|这幅|这个画面|图中|里面|原图|历史图片|引用|"
            + "它|继续|再|第[一二三四五六七八九十1-9]张)" );
    private static final Pattern IMAGE_EDIT = Pattern.compile(
        "(修改|改成|改为|加工|编辑|调整|换成|变成|添加|增加|删除|删掉|去掉|去除|"
            + "移除|擦除|消除|抹掉|保留|增强|减弱|风格|背景|颜色|色调|亮度|清晰度)" );
    private static final Pattern ORDINAL = Pattern.compile("第([一二三四五六七八九十1-9])张");

    private final MemoryProperties properties;
    private final Object databaseMonitor = new Object();
    private Path databasePath;
    private Path imageDirectory;

    public ConversationMemoryService(MemoryProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!properties.isEnabled()) {
            log.info("对话记忆已禁用");
            return;
        }
        Path dataDirectory = properties.getDataDirectory().toAbsolutePath().normalize();
        databasePath = dataDirectory.resolve("memory.db");
        imageDirectory = dataDirectory.resolve("images");
        Files.createDirectories(imageDirectory);
        initializeSchema();
        log.info(
            "对话记忆已启用：maxEntriesPerUser={}，maxImagesPerUser={}，maxImageBytesPerUser={}",
            properties.getMaxEntriesPerUser(),
            properties.getMaxImagesPerUser(),
            properties.getMaxImageBytesPerUser());
    }

    public AgentRequest prepare(AgentRequest request) {
        if (!properties.isEnabled()) {
            return request;
        }
        try {
            List<MemoryMessage> history = loadRecentMessages(request.userId());
            List<ImageAsset> referencedImages = shouldResolveImage(request.text())
                ? loadReferencedImages(request.userId(), request.text())
                : List.of();
            return request.withMemory(history, referencedImages);
        } catch (Exception exception) {
            log.warn("读取用户对话记忆失败，userId={}", request.userId(), exception);
            return request;
        }
    }

    public ReplyMode getReplyMode(String userId) {
        if (!properties.isEnabled() || userId == null || userId.isBlank()) {
            return ReplyMode.TEXT;
        }
        String sql = "SELECT reply_mode FROM user_preferences WHERE user_id = ?";
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            try {
                                return ReplyMode.valueOf(result.getString(1));
                            } catch (IllegalArgumentException ignored) {
                                return ReplyMode.TEXT;
                            }
                        }
                    }
                }
            }
        } catch (SQLException exception) {
            log.warn("读取用户回复模式失败，userId={}", userId, exception);
        }
        return ReplyMode.TEXT;
    }

    public boolean setReplyMode(String userId, ReplyMode mode) {
        if (!properties.isEnabled() || userId == null || userId.isBlank() || mode == null) {
            return false;
        }
        String sql = """
            INSERT INTO user_preferences(user_id, reply_mode, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                reply_mode = excluded.reply_mode,
                updated_at = excluded.updated_at
            """;
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, userId);
                    statement.setString(2, mode.name());
                    statement.setLong(3, Instant.now().toEpochMilli());
                    statement.executeUpdate();
                    return true;
                }
            }
        } catch (SQLException exception) {
            log.warn("保存用户回复模式失败，userId={}，mode={}", userId, mode, exception);
            return false;
        }
    }

    public VoicePreference getVoicePreference(String userId) {
        if (!properties.isEnabled() || userId == null || userId.isBlank()) {
            return null;
        }
        String sql = """
            SELECT tts_voice, tts_voice_name, tts_language
            FROM user_preferences WHERE user_id = ?
            """;
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            String voiceId = result.getString("tts_voice");
                            if (voiceId == null || voiceId.isBlank()) {
                                return null;
                            }
                            return new VoicePreference(
                                voiceId,
                                result.getString("tts_voice_name"),
                                result.getString("tts_language"));
                        }
                    }
                }
            }
        } catch (SQLException exception) {
            log.warn("读取用户音色偏好失败，userId={}", userId, exception);
        }
        return null;
    }

    public boolean setVoicePreference(String userId, VoicePreference preference) {
        if (!properties.isEnabled() || userId == null || userId.isBlank() || preference == null
            || preference.voiceId() == null || preference.voiceId().isBlank()) {
            return false;
        }
        String sql = """
            INSERT INTO user_preferences(
                user_id, reply_mode, tts_voice, tts_voice_name, tts_language, updated_at)
            VALUES (?, 'TEXT', ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                tts_voice = excluded.tts_voice,
                tts_voice_name = excluded.tts_voice_name,
                tts_language = excluded.tts_language,
                updated_at = excluded.updated_at
            """;
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, userId);
                    statement.setString(2, preference.voiceId());
                    statement.setString(3, preference.displayName());
                    statement.setString(4, preference.languageType());
                    statement.setLong(5, Instant.now().toEpochMilli());
                    return statement.executeUpdate() > 0;
                }
            }
        } catch (SQLException exception) {
            log.warn("保存用户音色偏好失败，userId={}，voice={}",
                userId, preference.voiceId(), exception);
            return false;
        }
    }

    public boolean clearVoicePreference(String userId) {
        if (!properties.isEnabled() || userId == null || userId.isBlank()) {
            return false;
        }
        String sql = """
            UPDATE user_preferences SET
                tts_voice = NULL,
                tts_voice_name = NULL,
                tts_language = NULL,
                updated_at = ?
            WHERE user_id = ?
            """;
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, Instant.now().toEpochMilli());
                    statement.setString(2, userId);
                    statement.executeUpdate();
                    return true;
                }
            }
        } catch (SQLException exception) {
            log.warn("清除用户音色偏好失败，userId={}", userId, exception);
            return false;
        }
    }

    public AgentRequest attachLatestImage(AgentRequest request) {
        if (!properties.isEnabled() || request == null
            || !request.images().isEmpty() || !request.referencedImages().isEmpty()) {
            return request;
        }
        try {
            return request.withMemory(
                request.history(), loadReferencedImages(request.userId(), "上一张图片"));
        } catch (Exception exception) {
            log.warn("读取图片任务参考图失败，userId={}", request.userId(), exception);
            return request;
        }
    }

    public void rememberUserRequest(AgentRequest request) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            synchronized (databaseMonitor) {
                if (!request.text().isBlank()) {
                    insertEntry(
                        request.userId(),
                        request.messageId(),
                        "user",
                        "TEXT",
                        request.text(),
                        null,
                        dedupeKey(request, "user", "TEXT", 0));
                }
                saveImages(
                    request.userId(),
                    request.messageId(),
                    "USER",
                    request.images(),
                    "用户发送的图片");
                prune(request.userId());
            }
        } catch (Exception exception) {
            log.warn("保存用户对话记忆失败，userId={}", request.userId(), exception);
        }
    }

    public void rememberAgentResult(
        AgentType type,
        AgentRequest request,
        AgentResponse response) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            synchronized (databaseMonitor) {
                if (!response.text().isBlank()) {
                    insertEntry(
                        request.userId(),
                        request.messageId(),
                        "assistant",
                        type.name(),
                        response.text(),
                        null,
                        dedupeKey(request, "assistant", type.name(), 0));
                }
                if (type == AgentType.VISION && !response.text().isBlank()) {
                    updateUserImageDescriptions(
                        request.userId(), request.messageId(), response.text());
                }
                saveImages(
                    request.userId(),
                    request.messageId(),
                    "GENERATED",
                    response.images(),
                    request.text().isBlank() ? response.text() : request.text());
                prune(request.userId());
            }
        } catch (Exception exception) {
            log.warn("保存 Agent 对话记忆失败，userId={}，type={}",
                request.userId(), type, exception);
        }
    }

    List<MemoryMessage> loadRecentMessages(String userId) throws SQLException {
        List<MemoryMessage> messages = new ArrayList<>();
        String sql = """
            SELECT role, kind, text
            FROM memory_entries
            WHERE user_id = ?
            ORDER BY id DESC
            LIMIT ?
            """;
        synchronized (databaseMonitor) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, userId);
                statement.setInt(2, Math.max(1, properties.getPromptEntries()));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String kind = result.getString("kind");
                        String text = result.getString("text");
                        if (text == null || text.isBlank()) {
                            continue;
                        }
                        String content = "IMAGE".equals(kind) ? "[图片：" + text + "]" : text;
                        messages.add(new MemoryMessage(result.getString("role"), content));
                    }
                }
            }
        }
        Collections.reverse(messages);
        return messages;
    }

    public String getLatestAssistantText(String userId) {
        if (!properties.isEnabled() || userId == null || userId.isBlank()) {
            return "";
        }
        String sql = """
            SELECT text FROM memory_entries
            WHERE user_id = ? AND role = 'assistant'
                AND text IS NOT NULL AND trim(text) <> ''
            ORDER BY id DESC LIMIT 1
            """;
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next() ? result.getString(1) : "";
                    }
                }
            }
        } catch (SQLException exception) {
            log.warn("读取最近一条机器人回复失败，userId={}", userId, exception);
            return "";
        }
    }

    public String getLatestExportableAssistantText(String userId) {
        if (!properties.isEnabled() || userId == null || userId.isBlank()) {
            return "";
        }
        String sql = """
            SELECT text FROM memory_entries
            WHERE user_id = ? AND role = 'assistant'
                AND text IS NOT NULL AND trim(text) <> ''
            ORDER BY id DESC LIMIT 30
            """;
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            String text = result.getString(1);
                            if (isExportableAssistantText(text)) {
                                return text;
                            }
                        }
                    }
                }
            }
        } catch (SQLException exception) {
            log.warn("读取最近可导出回复失败，userId={}", userId, exception);
        }
        return "";
    }

    List<ImageAsset> loadReferencedImages(String userId, String userText)
        throws SQLException, IOException {
        List<StoredImage> storedImages = new ArrayList<>();
        String sql = """
            SELECT image_id, file_path, media_type, file_name, description
            FROM memory_images
            WHERE user_id = ?
            ORDER BY created_at DESC, rowid DESC
            LIMIT ?
            """;
        synchronized (databaseMonitor) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, userId);
                statement.setInt(2, Math.max(1, properties.getMaxImagesPerUser()));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        storedImages.add(new StoredImage(
                            result.getString("image_id"),
                            Path.of(result.getString("file_path")),
                            result.getString("media_type"),
                            result.getString("file_name"),
                            result.getString("description")));
                    }
                }
            }
        }
        if (storedImages.isEmpty()) {
            return List.of();
        }

        int selectedIndex = 0;
        Matcher ordinal = ORDINAL.matcher(userText == null ? "" : userText);
        if (ordinal.find()) {
            int number = chineseNumber(ordinal.group(1));
            Collections.reverse(storedImages);
            selectedIndex = Math.min(Math.max(0, number - 1), storedImages.size() - 1);
        }
        StoredImage selected = storedImages.get(selectedIndex);
        if (!Files.isRegularFile(selected.path())) {
            return List.of();
        }
        return List.of(new ImageAsset(
            Files.readAllBytes(selected.path()),
            selected.mediaType(),
            selected.fileName()));
    }

    private void initializeSchema() throws SQLException {
        synchronized (databaseMonitor) {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS memory_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id TEXT NOT NULL,
                        message_id INTEGER,
                        role TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        text TEXT,
                        image_id TEXT,
                        dedupe_key TEXT NOT NULL UNIQUE,
                        created_at INTEGER NOT NULL
                    )
                    """);
                statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_memory_entries_user
                    ON memory_entries(user_id, id DESC)
                    """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS memory_images (
                        image_id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        message_id INTEGER,
                        source TEXT NOT NULL,
                        sequence_no INTEGER NOT NULL,
                        file_path TEXT NOT NULL,
                        media_type TEXT NOT NULL,
                        file_name TEXT NOT NULL,
                        description TEXT,
                        size_bytes INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        UNIQUE(user_id, message_id, source, sequence_no)
                    )
                    """);
                statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_memory_images_user
                    ON memory_images(user_id, created_at DESC)
                    """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS user_preferences (
                        user_id TEXT PRIMARY KEY,
                        reply_mode TEXT NOT NULL DEFAULT 'TEXT',
                        tts_voice TEXT,
                        tts_voice_name TEXT,
                        tts_language TEXT,
                        updated_at INTEGER NOT NULL
                    )
                    """);
                ensureColumn(connection, "user_preferences", "tts_voice", "TEXT");
                ensureColumn(connection, "user_preferences", "tts_voice_name", "TEXT");
                ensureColumn(connection, "user_preferences", "tts_language", "TEXT");
            }
        }
    }

    private static void ensureColumn(
        Connection connection,
        String table,
        String column,
        String definition) throws SQLException {
        boolean exists = false;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                    "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }

    private void saveImages(
        String userId,
        Long messageId,
        String source,
        List<ImageAsset> images,
        String description) throws SQLException, IOException {
        int sequence = 0;
        for (ImageAsset image : images) {
            if (image.data().length == 0) {
                continue;
            }
            if (imageAlreadyExists(userId, messageId, source, sequence)) {
                sequence++;
                continue;
            }
            String imageId = UUID.randomUUID().toString();
            Path userDirectory = imageDirectory.resolve(hashUserId(userId));
            Files.createDirectories(userDirectory);
            String extension = safeExtension(image.fileName(), image.mediaType());
            Path imagePath = userDirectory.resolve(imageId + extension);
            byte[] data = image.data();
            Files.write(imagePath, data, StandardOpenOption.CREATE_NEW);

            String sql = """
                INSERT INTO memory_images(
                    image_id, user_id, message_id, source, sequence_no, file_path,
                    media_type, file_name, description, size_bytes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, imageId);
                statement.setString(2, userId);
                setNullableLong(statement, 3, messageId);
                statement.setString(4, source);
                statement.setInt(5, sequence);
                statement.setString(6, imagePath.toAbsolutePath().toString());
                statement.setString(7, image.mediaType());
                statement.setString(8, image.fileName());
                statement.setString(9, description);
                statement.setLong(10, data.length);
                statement.setLong(11, Instant.now().toEpochMilli());
                statement.executeUpdate();
            } catch (SQLException exception) {
                Files.deleteIfExists(imagePath);
                throw exception;
            }
            insertEntry(
                userId,
                messageId,
                "USER".equals(source) ? "user" : "assistant",
                "IMAGE",
                description,
                imageId,
                userId + "|" + messageId + "|" + source + "|IMAGE|" + sequence);
            sequence++;
        }
    }

    private boolean imageAlreadyExists(
        String userId,
        Long messageId,
        String source,
        int sequence) throws SQLException {
        String sql = """
            SELECT 1 FROM memory_images
            WHERE user_id = ? AND message_id IS ? AND source = ? AND sequence_no = ?
            """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            setNullableLong(statement, 2, messageId);
            statement.setString(3, source);
            statement.setInt(4, sequence);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void insertEntry(
        String userId,
        Long messageId,
        String role,
        String kind,
        String text,
        String imageId,
        String dedupeKey) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO memory_entries(
                user_id, message_id, role, kind, text, image_id, dedupe_key, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            setNullableLong(statement, 2, messageId);
            statement.setString(3, role);
            statement.setString(4, kind);
            statement.setString(5, text);
            statement.setString(6, imageId);
            statement.setString(7, dedupeKey);
            statement.setLong(8, Instant.now().toEpochMilli());
            statement.executeUpdate();
        }
    }

    private void updateUserImageDescriptions(String userId, Long messageId, String description)
        throws SQLException {
        String imageSql = """
            UPDATE memory_images SET description = ?
            WHERE user_id = ? AND message_id IS ? AND source = 'USER'
            """;
        String entrySql = """
            UPDATE memory_entries SET text = ?
            WHERE user_id = ? AND message_id IS ? AND kind = 'IMAGE' AND role = 'user'
            """;
        try (Connection connection = connection()) {
            try (PreparedStatement statement = connection.prepareStatement(imageSql)) {
                statement.setString(1, description);
                statement.setString(2, userId);
                setNullableLong(statement, 3, messageId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(entrySql)) {
                statement.setString(1, description);
                statement.setString(2, userId);
                setNullableLong(statement, 3, messageId);
                statement.executeUpdate();
            }
        }
    }

    private void prune(String userId) throws SQLException, IOException {
        pruneEntries(userId);
        List<ImageRow> images = new ArrayList<>();
        String selectSql = """
            SELECT image_id, file_path, size_bytes
            FROM memory_images
            WHERE user_id = ?
            ORDER BY created_at DESC, rowid DESC
            """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    images.add(new ImageRow(
                        result.getString("image_id"),
                        Path.of(result.getString("file_path")),
                        result.getLong("size_bytes")));
                }
            }
        }

        long retainedBytes = 0;
        int retainedCount = 0;
        for (ImageRow image : images) {
            boolean keep = retainedCount < Math.max(0, properties.getMaxImagesPerUser())
                && retainedBytes + image.sizeBytes()
                    <= Math.max(0, properties.getMaxImageBytesPerUser());
            if (keep) {
                retainedCount++;
                retainedBytes += image.sizeBytes();
                continue;
            }
            deleteImage(image);
        }
    }

    private void pruneEntries(String userId) throws SQLException {
        String sql = """
            DELETE FROM memory_entries
            WHERE user_id = ? AND id NOT IN (
                SELECT id FROM memory_entries
                WHERE user_id = ?
                ORDER BY id DESC
                LIMIT ?
            )
            """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, userId);
            statement.setInt(3, Math.max(1, properties.getMaxEntriesPerUser()));
            statement.executeUpdate();
        }
    }

    private void deleteImage(ImageRow image) throws SQLException, IOException {
        Files.deleteIfExists(image.path());
        try (Connection connection = connection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM memory_entries WHERE image_id = ?")) {
                statement.setString(1, image.imageId());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM memory_images WHERE image_id = ?")) {
                statement.setString(1, image.imageId());
                statement.executeUpdate();
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private static boolean shouldResolveImage(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return IMAGE_REFERENCE.matcher(text).find() || IMAGE_EDIT.matcher(text).find();
    }

    private static boolean isExportableAssistantText(String text) {
        if (text == null || text.isBlank()) return false;
        String value = text.trim();
        return !value.startsWith("文件已生成")
            && !value.startsWith("文件发送完成")
            && !value.startsWith("文件已读取完成")
            && !value.startsWith("正在发送")
            && !value.equals("图片已生成。")
            && !value.equals("我正在思考，请稍等。");
    }

    private static String dedupeKey(
        AgentRequest request,
        String role,
        String kind,
        int sequence) {
        String messagePart = request.messageId() == null
            ? UUID.randomUUID().toString() : request.messageId().toString();
        return request.userId() + "|" + messagePart + "|" + role + "|" + kind + "|" + sequence;
    }

    private static String hashUserId(String userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(userId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String safeExtension(String fileName, String mediaType) {
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && fileName.length() - dot <= 6) {
                String extension = fileName.substring(dot).toLowerCase(Locale.ROOT);
                if (extension.matches("\\.(png|jpe?g|webp|gif)")) {
                    return extension;
                }
            }
        }
        return switch (mediaType == null ? "" : mediaType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }

    private static void setNullableLong(
        PreparedStatement statement,
        int index,
        Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static int chineseNumber(String value) {
        return switch (value) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> Integer.parseInt(value);
        };
    }

    private record StoredImage(
        String imageId,
        Path path,
        String mediaType,
        String fileName,
        String description) {
    }

    private record ImageRow(String imageId, Path path, long sizeBytes) {
    }
}
