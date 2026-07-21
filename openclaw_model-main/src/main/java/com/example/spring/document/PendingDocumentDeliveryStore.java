package com.example.spring.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

@Service
public class PendingDocumentDeliveryStore implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(PendingDocumentDeliveryStore.class);
    private final DocumentProperties properties;
    private final Object monitor = new Object();
    private Path databasePath;
    private Path pendingDirectory;

    public PendingDocumentDeliveryStore(DocumentProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!properties.isEnabled()) return;
        Path root = properties.getDataDirectory().toAbsolutePath().normalize();
        databasePath = root.resolve("memory.db");
        pendingDirectory = root.resolve("pending-document-delivery");
        Files.createDirectories(pendingDirectory);
        synchronized (monitor) {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pending_document_delivery (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id TEXT NOT NULL,
                        message_id INTEGER,
                        file_path TEXT NOT NULL UNIQUE,
                        file_name TEXT NOT NULL,
                        media_type TEXT NOT NULL,
                        description TEXT,
                        created_at INTEGER NOT NULL
                    )
                    """);
            }
        }
    }

    public boolean save(String userId, Long messageId, GeneratedDocument document) {
        if (!properties.isEnabled() || document == null || document.data().length == 0) return false;
        Path path = pendingDirectory.resolve(UUID.randomUUID() + extension(document.fileName()));
        try {
            Files.write(path, document.data(), StandardOpenOption.CREATE_NEW);
            synchronized (monitor) {
                try (Connection connection = connection();
                     PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO pending_document_delivery(
                            user_id, message_id, file_path, file_name, media_type,
                            description, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    statement.setString(1, userId);
                    if (messageId == null) statement.setNull(2, java.sql.Types.BIGINT);
                    else statement.setLong(2, messageId);
                    statement.setString(3, path.toString());
                    statement.setString(4, document.fileName());
                    statement.setString(5, document.mediaType());
                    statement.setString(6, document.description());
                    statement.setLong(7, Instant.now().toEpochMilli());
                    statement.executeUpdate();
                }
            }
            return true;
        } catch (SQLException | IOException exception) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            log.warn("保存待重试文件失败，userId={}，fileName={}",
                userId, document.fileName(), exception);
            return false;
        }
    }

    public List<PendingDocumentDelivery> loadPending() {
        if (!properties.isEnabled()) return List.of();
        List<PendingDocumentDelivery> result = new ArrayList<>();
        List<Long> staleIds = new ArrayList<>();
        String sql = "SELECT * FROM pending_document_delivery ORDER BY id";
        try {
            synchronized (monitor) {
                try (Connection connection = connection()) {
                    try (Statement statement = connection.createStatement();
                         ResultSet rows = statement.executeQuery(sql)) {
                        while (rows.next()) {
                            Path path = Path.of(rows.getString("file_path"));
                            if (!Files.isRegularFile(path)) {
                                staleIds.add(rows.getLong("id"));
                                continue;
                            }
                            long messageValue = rows.getLong("message_id");
                            Long messageId = rows.wasNull() ? null : messageValue;
                            result.add(new PendingDocumentDelivery(
                                rows.getLong("id"), rows.getString("user_id"), messageId,
                                new GeneratedDocument(Files.readAllBytes(path),
                                    rows.getString("media_type"), rows.getString("file_name"),
                                    rows.getString("description"))));
                        }
                    }
                    for (Long staleId : staleIds) deleteRow(connection, staleId);
                }
            }
        } catch (SQLException | IOException exception) {
            log.warn("读取待重试文件失败", exception);
        }
        return result;
    }

    public void complete(PendingDocumentDelivery pending) {
        if (pending == null) return;
        synchronized (monitor) {
            try (Connection connection = connection();
                 PreparedStatement select = connection.prepareStatement(
                     "SELECT file_path FROM pending_document_delivery WHERE id = ?")) {
                select.setLong(1, pending.id());
                Path path = null;
                try (ResultSet result = select.executeQuery()) {
                    if (result.next()) path = Path.of(result.getString(1));
                }
                deleteRow(connection, pending.id());
                if (path != null) Files.deleteIfExists(path);
            } catch (SQLException | IOException exception) {
                log.warn("清理已发送文件失败，id={}", pending.id(), exception);
            }
        }
    }

    private static void deleteRow(Connection connection, long id) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
            "DELETE FROM pending_document_delivery WHERE id = ?")) {
            delete.setLong(1, id);
            delete.executeUpdate();
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private static String extension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot >= 0 && fileName.length() - dot <= 8 ? fileName.substring(dot) : ".bin";
    }
}
