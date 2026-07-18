package com.example.spring.task;

import com.example.spring.memory.MemoryProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ImageTaskSessionService implements InitializingBean {
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private final MemoryProperties memoryProperties;
    private final ObjectMapper objectMapper;
    private final Object databaseMonitor = new Object();
    private String jdbcUrl;

    @Autowired
    public ImageTaskSessionService(MemoryProperties memoryProperties) {
        this(memoryProperties, new ObjectMapper());
    }

    ImageTaskSessionService(MemoryProperties memoryProperties, ObjectMapper objectMapper) {
        this.memoryProperties = memoryProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!memoryProperties.isEnabled()) {
            return;
        }
        var dataDirectory = memoryProperties.getDataDirectory().toAbsolutePath().normalize();
        java.nio.file.Files.createDirectories(dataDirectory);
        jdbcUrl = "jdbc:sqlite:" + dataDirectory.resolve("memory.db");
        synchronized (databaseMonitor) {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS task_sessions (
                        user_id TEXT PRIMARY KEY,
                        task_type TEXT NOT NULL,
                        task_status TEXT NOT NULL,
                        brief_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL
                    )
                    """);
            }
        }
    }

    public Optional<ImageTaskSession> loadActive(String userId) {
        if (!available() || userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        String sql = """
            SELECT task_status, brief_json, created_at, updated_at, expires_at
            FROM task_sessions
            WHERE user_id = ? AND task_type = 'IMAGE'
            """;
        synchronized (databaseMonitor) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, userId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Optional.empty();
                    }
                    TaskStatus status = TaskStatus.valueOf(result.getString("task_status"));
                    Instant expiresAt = Instant.ofEpochMilli(result.getLong("expires_at"));
                    if (expiresAt.isBefore(Instant.now())
                        || status == TaskStatus.COMPLETED || status == TaskStatus.CANCELLED) {
                        return Optional.empty();
                    }
                    return Optional.of(new ImageTaskSession(
                        userId,
                        status,
                        objectMapper.readValue(result.getString("brief_json"), ImageTaskBrief.class),
                        Instant.ofEpochMilli(result.getLong("created_at")),
                        Instant.ofEpochMilli(result.getLong("updated_at")),
                        expiresAt));
                }
            } catch (SQLException | JsonProcessingException exception) {
                throw new IllegalStateException("读取图片任务状态失败", exception);
            }
        }
    }

    public void save(String userId, TaskStatus status, ImageTaskBrief brief) {
        if (!available()) {
            return;
        }
        Instant now = Instant.now();
        String sql = """
            INSERT INTO task_sessions(
                user_id, task_type, task_status, brief_json, created_at, updated_at, expires_at)
            VALUES (?, 'IMAGE', ?, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                task_type = 'IMAGE', task_status = excluded.task_status,
                brief_json = excluded.brief_json, updated_at = excluded.updated_at,
                expires_at = excluded.expires_at
            """;
        synchronized (databaseMonitor) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, userId);
                statement.setString(2, status.name());
                statement.setString(3, objectMapper.writeValueAsString(
                    brief == null ? ImageTaskBrief.empty() : brief));
                statement.setLong(4, now.toEpochMilli());
                statement.setLong(5, now.toEpochMilli());
                statement.setLong(6, now.plus(SESSION_TTL).toEpochMilli());
                statement.executeUpdate();
            } catch (SQLException | JsonProcessingException exception) {
                throw new IllegalStateException("保存图片任务状态失败", exception);
            }
        }
    }

    public void complete(String userId) {
        updateStatus(userId, TaskStatus.COMPLETED);
    }

    public void cancel(String userId) {
        updateStatus(userId, TaskStatus.CANCELLED);
    }

    private void updateStatus(String userId, TaskStatus status) {
        if (!available()) {
            return;
        }
        String sql = "UPDATE task_sessions SET task_status = ?, updated_at = ? WHERE user_id = ?";
        synchronized (databaseMonitor) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, status.name());
                statement.setLong(2, Instant.now().toEpochMilli());
                statement.setString(3, userId);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("更新图片任务状态失败", exception);
            }
        }
    }

    private boolean available() {
        return memoryProperties.isEnabled() && jdbcUrl != null;
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
