package com.example.spring.speech;

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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

@Service
public class PendingVoiceReplyStore implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(PendingVoiceReplyStore.class);

    private final MemoryProperties memoryProperties;
    private final Object monitor = new Object();
    private Path databasePath;
    private Path assetDirectory;

    public PendingVoiceReplyStore(MemoryProperties memoryProperties) {
        this.memoryProperties = memoryProperties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Path dataDirectory = memoryProperties.getDataDirectory().toAbsolutePath().normalize();
        Files.createDirectories(dataDirectory);
        databasePath = dataDirectory.resolve("memory.db");
        assetDirectory = dataDirectory.resolve("pending-voice-replies");
        Files.createDirectories(assetDirectory);
        initializeSchema();
        cleanupExpired();
    }

    public boolean saveRemaining(
        String userId,
        long messageId,
        List<VoiceDeliveryAsset> assets,
        int startIndex,
        boolean firstVoiceSent) {
        if (assets == null || startIndex < 0 || startIndex >= assets.size()) {
            return false;
        }
        String batchId = UUID.randomUUID().toString();
        Path batchDirectory = assetDirectory.resolve(batchId);
        try {
            synchronized (monitor) {
                Files.createDirectories(batchDirectory);
                try (Connection connection = connection()) {
                    connection.setAutoCommit(false);
                    String sql = """
                        INSERT INTO pending_voice_replies(
                            batch_id, user_id, message_id, sequence_no, text,
                            silk_path, mp3_path, duration_ms, voice_sent, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """;
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        for (int index = startIndex; index < assets.size(); index++) {
                            VoiceDeliveryAsset asset = assets.get(index);
                            int sequence = index + 1;
                            Path silk = batchDirectory.resolve(sequence + ".silk");
                            Path mp3 = batchDirectory.resolve(sequence + ".mp3");
                            Files.write(silk, asset.silkData(),
                                StandardOpenOption.CREATE_NEW);
                            Files.write(mp3, asset.mp3Data(),
                                StandardOpenOption.CREATE_NEW);
                            statement.setString(1, batchId);
                            statement.setString(2, userId);
                            statement.setLong(3, messageId);
                            statement.setInt(4, sequence);
                            statement.setString(5, asset.text());
                            statement.setString(6, silk.toString());
                            statement.setString(7, mp3.toString());
                            statement.setLong(8, asset.durationMs());
                            statement.setInt(9, index == startIndex && firstVoiceSent ? 1 : 0);
                            statement.setLong(10, Instant.now().toEpochMilli());
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                    connection.commit();
                }
            }
            return true;
        } catch (Exception exception) {
            log.warn("保存待重试语音失败，userId={}，messageId={}",
                userId, messageId, exception);
            deleteDirectory(batchDirectory);
            return false;
        }
    }

    public List<PendingVoiceReply> loadPending() {
        String sql = """
            SELECT id, batch_id, user_id, message_id, sequence_no, text,
                silk_path, mp3_path, duration_ms, voice_sent
            FROM pending_voice_replies
            ORDER BY created_at, batch_id, sequence_no
            """;
        List<PendingVoiceReply> replies = new ArrayList<>();
        try {
            synchronized (monitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql);
                     ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        Path silk = Path.of(result.getString("silk_path"));
                        Path mp3 = Path.of(result.getString("mp3_path"));
                        if (!Files.isRegularFile(silk) || !Files.isRegularFile(mp3)) {
                            continue;
                        }
                        replies.add(new PendingVoiceReply(
                            result.getLong("id"),
                            result.getString("batch_id"),
                            result.getString("user_id"),
                            result.getLong("message_id"),
                            result.getInt("sequence_no"),
                            new VoiceDeliveryAsset(
                                result.getString("text"),
                                Files.readAllBytes(silk),
                                result.getLong("duration_ms"),
                                Files.readAllBytes(mp3)),
                            result.getInt("voice_sent") != 0));
                    }
                }
            }
        } catch (Exception exception) {
            log.warn("读取待重试语音失败", exception);
        }
        return replies;
    }

    public void markVoiceSent(long id) {
        executeUpdate(
            "UPDATE pending_voice_replies SET voice_sent = 1 WHERE id = ?", id);
    }

    public void complete(PendingVoiceReply reply) {
        String select = "SELECT silk_path, mp3_path FROM pending_voice_replies WHERE id = ?";
        try {
            synchronized (monitor) {
                Path silk = null;
                Path mp3 = null;
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(select)) {
                    statement.setLong(1, reply.id());
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            silk = Path.of(result.getString(1));
                            mp3 = Path.of(result.getString(2));
                        }
                    }
                }
                executeUpdate("DELETE FROM pending_voice_replies WHERE id = ?", reply.id());
                if (silk != null) Files.deleteIfExists(silk);
                if (mp3 != null) Files.deleteIfExists(mp3);
                if (silk != null) deleteDirectoryIfEmpty(silk.getParent());
            }
        } catch (Exception exception) {
            log.warn("清理已重发语音失败，id={}", reply.id(), exception);
        }
    }

    private void initializeSchema() throws SQLException {
        synchronized (monitor) {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pending_voice_replies (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        batch_id TEXT NOT NULL,
                        user_id TEXT NOT NULL,
                        message_id INTEGER NOT NULL,
                        sequence_no INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        silk_path TEXT NOT NULL,
                        mp3_path TEXT NOT NULL,
                        duration_ms INTEGER NOT NULL,
                        voice_sent INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        UNIQUE(batch_id, sequence_no)
                    )
                    """);
            }
        }
    }

    private void cleanupExpired() {
        long cutoff = Instant.now().minus(Duration.ofHours(24)).toEpochMilli();
        List<PendingVoiceReply> expired = loadBefore(cutoff);
        for (PendingVoiceReply reply : expired) {
            complete(reply);
        }
    }

    private List<PendingVoiceReply> loadBefore(long cutoff) {
        String sql = """
            SELECT id, batch_id, user_id, message_id, sequence_no, text,
                silk_path, mp3_path, duration_ms, voice_sent
            FROM pending_voice_replies WHERE created_at < ?
            """;
        List<PendingVoiceReply> results = new ArrayList<>();
        try {
            synchronized (monitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, cutoff);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            results.add(new PendingVoiceReply(
                                result.getLong("id"), result.getString("batch_id"),
                                result.getString("user_id"), result.getLong("message_id"),
                                result.getInt("sequence_no"),
                                new VoiceDeliveryAsset(result.getString("text"), new byte[0],
                                    result.getLong("duration_ms"), new byte[0]),
                                result.getInt("voice_sent") != 0));
                        }
                    }
                }
            }
        } catch (SQLException exception) {
            log.warn("读取过期语音重试记录失败", exception);
        }
        return results;
    }

    private void executeUpdate(String sql, long id) {
        try {
            synchronized (monitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, id);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException exception) {
            log.warn("更新语音重试记录失败，id={}", id, exception);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private static void deleteDirectoryIfEmpty(Path directory) throws Exception {
        if (directory == null || !Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            if (files.findAny().isEmpty()) Files.deleteIfExists(directory);
        }
    }

    private static void deleteDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            files.forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
            Files.deleteIfExists(directory);
        } catch (Exception ignored) {
        }
    }
}
