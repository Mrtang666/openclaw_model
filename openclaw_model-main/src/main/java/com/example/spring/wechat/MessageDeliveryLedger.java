package com.example.spring.wechat;

import com.example.spring.memory.MemoryProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

@Service
public class MessageDeliveryLedger implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(MessageDeliveryLedger.class);
    private static final List<MessageReplyStatus> RECOVERABLE = List.of(
        MessageReplyStatus.READY,
        MessageReplyStatus.PROCESSING,
        MessageReplyStatus.REPLYING,
        MessageReplyStatus.FAILED);

    private final MemoryProperties properties;
    private final Object databaseMonitor = new Object();
    private Path databasePath;
    private Path pendingVoiceDirectory;

    public MessageDeliveryLedger(MemoryProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Path dataDirectory = properties.getDataDirectory().toAbsolutePath().normalize();
        Files.createDirectories(dataDirectory);
        databasePath = dataDirectory.resolve("memory.db");
        pendingVoiceDirectory = dataDirectory.resolve("pending-voice");
        Files.createDirectories(pendingVoiceDirectory);
        initializeSchema();
        cleanup();
    }

    public boolean register(
        String userId,
        long messageId,
        String sourceType,
        String originalText) {
        String sql = """
            INSERT OR IGNORE INTO pending_messages(
                user_id, message_id, source_type, original_text, status, received_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        long now = Instant.now().toEpochMilli();
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, userId);
                    statement.setLong(2, messageId);
                    statement.setString(3, sourceType);
                    statement.setString(4, originalText);
                    statement.setString(5, MessageReplyStatus.RECEIVED.name());
                    statement.setLong(6, now);
                    statement.setLong(7, now);
                    return statement.executeUpdate() > 0;
                }
            }
        } catch (SQLException exception) {
            log.warn("记录微信消息投递状态失败，userId={}，messageId={}",
                userId, messageId, exception);
            return true;
        }
    }

    public void ready(String userId, long messageId, String normalizedText) {
        update(userId, messageId, MessageReplyStatus.READY, normalizedText, null, false);
    }

    public void mark(String userId, long messageId, MessageReplyStatus status) {
        update(userId, messageId, status, null, null,
            status == MessageReplyStatus.REPLYING || status == MessageReplyStatus.FAILED);
    }

    public void complete(String userId, long messageId, DeliveryResult result) {
        MessageReplyStatus status = result.success()
            ? MessageReplyStatus.REPLIED
            : result.partial() ? MessageReplyStatus.PARTIAL : MessageReplyStatus.FAILED;
        update(userId, messageId, status, null, result.error(), !result.success());
        if (result.success() || result.partial()) {
            deleteVoicePayloads(userId, messageId);
        }
    }

    public void saveVoicePayload(String userId, long messageId, int sequence, byte[] data) {
        if (data == null || data.length == 0 || data.length > 10L * 1024 * 1024) {
            return;
        }
        try {
            synchronized (databaseMonitor) {
                pruneVoicePayloads(userId);
                String safeUser = Integer.toUnsignedString(userId.hashCode(), 16);
                Path directory = pendingVoiceDirectory.resolve(safeUser);
                Files.createDirectories(directory);
                Path file = directory.resolve(messageId + "-" + sequence + ".bin");
                Files.write(file, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                String sql = """
                    INSERT INTO pending_voice_payloads(
                        user_id, message_id, sequence_no, file_path, size_bytes, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(user_id, message_id, sequence_no) DO UPDATE SET
                        file_path = excluded.file_path,
                        size_bytes = excluded.size_bytes,
                        created_at = excluded.created_at
                    """;
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, userId);
                    statement.setLong(2, messageId);
                    statement.setInt(3, sequence);
                    statement.setString(4, file.toString());
                    statement.setLong(5, data.length);
                    statement.setLong(6, Instant.now().toEpochMilli());
                    statement.executeUpdate();
                }
            }
        } catch (Exception exception) {
            log.warn("暂存待处理语音失败，userId={}，messageId={}，sequence={}",
                userId, messageId, sequence, exception);
        }
    }

    public List<byte[]> loadVoicePayloads(String userId, long messageId) {
        String sql = """
            SELECT file_path FROM pending_voice_payloads
            WHERE user_id = ? AND message_id = ?
            ORDER BY sequence_no
            """;
        List<byte[]> payloads = new ArrayList<>();
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, userId);
                    statement.setLong(2, messageId);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            Path path = Path.of(result.getString(1));
                            if (Files.isRegularFile(path)) {
                                payloads.add(Files.readAllBytes(path));
                            }
                        }
                    }
                }
            }
        } catch (Exception exception) {
            log.warn("读取待恢复语音失败，userId={}，messageId={}",
                userId, messageId, exception);
        }
        return payloads;
    }

    public List<PendingMessageRecord> findRecoverable() {
        return queryByStatuses(RECOVERABLE, null);
    }

    public List<PendingMessageRecord> findWaitingForUser(String userId) {
        return queryByStatuses(List.of(MessageReplyStatus.WAITING_CONFIRM), userId);
    }

    public void markRecoverableWaiting(String userId) {
        updateStatuses(userId, RECOVERABLE, MessageReplyStatus.WAITING_CONFIRM);
    }

    public void resolveWaiting(String userId, MessageReplyStatus status) {
        updateStatuses(userId, List.of(MessageReplyStatus.WAITING_CONFIRM), status);
    }

    public void cleanup() {
        long cutoff = Instant.now().minus(Duration.ofDays(30)).toEpochMilli();
        String sql = """
            DELETE FROM pending_messages
            WHERE updated_at < ? AND status IN ('REPLIED', 'PARTIAL', 'DECLINED', 'SUPERSEDED')
            """;
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, cutoff);
                    statement.executeUpdate();
                }
                cleanupExpiredVoicePayloads();
            }
        } catch (SQLException exception) {
            log.warn("清理历史消息投递记录失败", exception);
        }
    }

    private void update(
        String userId,
        long messageId,
        MessageReplyStatus status,
        String normalizedText,
        String error,
        boolean incrementAttempts) {
        String sql = """
            UPDATE pending_messages SET
                status = ?,
                normalized_text = COALESCE(?, normalized_text),
                last_error = ?,
                reply_attempts = reply_attempts + ?,
                updated_at = ?
            WHERE user_id = ? AND message_id = ?
            """;
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, status.name());
                    statement.setString(2, normalizedText);
                    statement.setString(3, error);
                    statement.setInt(4, incrementAttempts ? 1 : 0);
                    statement.setLong(5, Instant.now().toEpochMilli());
                    statement.setString(6, userId);
                    statement.setLong(7, messageId);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException exception) {
            log.warn("更新微信消息投递状态失败，userId={}，messageId={}，status={}",
                userId, messageId, status, exception);
        }
    }

    private void updateStatuses(
        String userId,
        List<MessageReplyStatus> from,
        MessageReplyStatus to) {
        String placeholders = String.join(",", from.stream().map(value -> "?").toList());
        String sql = "UPDATE pending_messages SET status = ?, updated_at = ? "
            + "WHERE user_id = ? AND status IN (" + placeholders + ")";
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    int index = 1;
                    statement.setString(index++, to.name());
                    statement.setLong(index++, Instant.now().toEpochMilli());
                    statement.setString(index++, userId);
                    for (MessageReplyStatus status : from) {
                        statement.setString(index++, status.name());
                    }
                    statement.executeUpdate();
                }
            }
        } catch (SQLException exception) {
            log.warn("批量更新微信消息投递状态失败，userId={}，status={}",
                userId, to, exception);
        }
    }

    private List<PendingMessageRecord> queryByStatuses(
        List<MessageReplyStatus> statuses,
        String userId) {
        String placeholders = String.join(",", statuses.stream().map(value -> "?").toList());
        String sql = "SELECT * FROM pending_messages WHERE status IN (" + placeholders + ")"
            + (userId == null ? "" : " AND user_id = ?")
            + " ORDER BY received_at, id";
        List<PendingMessageRecord> records = new ArrayList<>();
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    int index = 1;
                    for (MessageReplyStatus status : statuses) {
                        statement.setString(index++, status.name());
                    }
                    if (userId != null) {
                        statement.setString(index, userId);
                    }
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            records.add(map(result));
                        }
                    }
                }
            }
        } catch (SQLException exception) {
            log.warn("读取待回复微信消息失败，userId={}", userId, exception);
        }
        return records;
    }

    private void initializeSchema() throws SQLException {
        synchronized (databaseMonitor) {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pending_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id TEXT NOT NULL,
                        message_id INTEGER NOT NULL,
                        source_type TEXT NOT NULL,
                        original_text TEXT,
                        normalized_text TEXT,
                        status TEXT NOT NULL,
                        reply_attempts INTEGER NOT NULL DEFAULT 0,
                        last_error TEXT,
                        received_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        UNIQUE(user_id, message_id)
                    )
                    """);
                statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_pending_messages_status
                    ON pending_messages(status, user_id, received_at)
                    """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pending_voice_payloads (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id TEXT NOT NULL,
                        message_id INTEGER NOT NULL,
                        sequence_no INTEGER NOT NULL,
                        file_path TEXT NOT NULL,
                        size_bytes INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        UNIQUE(user_id, message_id, sequence_no)
                    )
                    """);
            }
        }
    }

    private void pruneVoicePayloads(String userId) throws SQLException {
        String sql = """
            SELECT id, file_path FROM pending_voice_payloads
            WHERE user_id = ? ORDER BY created_at DESC, id DESC
            """;
        List<Long> ids = new ArrayList<>();
        List<Path> paths = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                int retained = 0;
                while (result.next()) {
                    if (retained++ < 4) {
                        continue;
                    }
                    ids.add(result.getLong(1));
                    paths.add(Path.of(result.getString(2)));
                }
            }
        }
        deleteVoiceRows(ids, paths);
    }

    private void cleanupExpiredVoicePayloads() throws SQLException {
        long cutoff = Instant.now().minus(Duration.ofHours(24)).toEpochMilli();
        List<Long> ids = new ArrayList<>();
        List<Path> paths = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, file_path FROM pending_voice_payloads WHERE created_at < ?")) {
            statement.setLong(1, cutoff);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ids.add(result.getLong(1));
                    paths.add(Path.of(result.getString(2)));
                }
            }
        }
        deleteVoiceRows(ids, paths);
    }

    private void deleteVoicePayloads(String userId, long messageId) {
        List<Long> ids = new ArrayList<>();
        List<Path> paths = new ArrayList<>();
        try {
            synchronized (databaseMonitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(
                         "SELECT id, file_path FROM pending_voice_payloads "
                             + "WHERE user_id = ? AND message_id = ?")) {
                    statement.setString(1, userId);
                    statement.setLong(2, messageId);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            ids.add(result.getLong(1));
                            paths.add(Path.of(result.getString(2)));
                        }
                    }
                }
                deleteVoiceRows(ids, paths);
            }
        } catch (Exception exception) {
            log.warn("删除已完成语音暂存文件失败，userId={}，messageId={}",
                userId, messageId, exception);
        }
    }

    private void deleteVoiceRows(List<Long> ids, List<Path> paths) throws SQLException {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception exception) {
                log.debug("删除语音暂存文件失败，path={}", path, exception);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM pending_voice_payloads WHERE id = ?")) {
            for (Long id : ids) {
                statement.setLong(1, id);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private PendingMessageRecord map(ResultSet result) throws SQLException {
        return new PendingMessageRecord(
            result.getLong("id"),
            result.getString("user_id"),
            result.getLong("message_id"),
            result.getString("source_type"),
            result.getString("original_text"),
            result.getString("normalized_text"),
            MessageReplyStatus.valueOf(result.getString("status")),
            result.getInt("reply_attempts"),
            result.getString("last_error"),
            result.getLong("received_at"),
            result.getLong("updated_at"));
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }
}
