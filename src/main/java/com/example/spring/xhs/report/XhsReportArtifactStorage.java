package com.example.spring.xhs.report;

import com.example.spring.xhs.config.XhsScheduledReportProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class XhsReportArtifactStorage {

    private final Path root;

    public XhsReportArtifactStorage(XhsScheduledReportProperties properties) {
        this.root = Path.of(properties.storageDir()).toAbsolutePath().normalize();
    }

    public StoredArtifact store(long runId, String projectKey, String format, String fileName,
                                String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("报告文件不能为空");
        }
        String safeProject = safeSegment(projectKey, "project");
        String displayFileName = safeDisplayFileName(
                fileName, "report." + format.toLowerCase(java.util.Locale.ROOT));
        String safeFile = Long.toUnsignedString(System.nanoTime(), 36) + "-"
                + safeSegment(format, "report").toLowerCase(java.util.Locale.ROOT)
                + extension(displayFileName, format);
        Path directory = root.resolve(safeProject).resolve(Long.toString(runId)).normalize();
        Path target = directory.resolve(safeFile).normalize();
        ensureWithinRoot(target);
        try {
            Files.createDirectories(directory);
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            String storageKey = root.relativize(target).toString().replace('\\', '/');
            return new StoredArtifact(storageKey, displayFileName, contentType, bytes.length, sha256(bytes));
        } catch (IOException exception) {
            throw new IllegalStateException("报告文件保存失败", exception);
        }
    }

    public byte[] read(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("报告文件路径不能为空");
        }
        Path target = root.resolve(storageKey).normalize();
        ensureWithinRoot(target);
        try {
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw new IllegalStateException("报告文件不存在或无法读取", exception);
        }
    }

    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        Path target = root.resolve(storageKey).normalize();
        ensureWithinRoot(target);
        try {
            Files.deleteIfExists(target);
            removeEmptyParents(target.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("报告文件删除失败", exception);
        }
    }

    private void removeEmptyParents(Path directory) throws IOException {
        Path current = directory;
        while (current != null && !current.equals(root) && current.startsWith(root)) {
            try (var entries = Files.list(current)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    private void ensureWithinRoot(Path target) {
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("报告文件路径超出存储目录");
        }
    }

    private String safeSegment(String value, String fallback) {
        String result = (value == null ? "" : value.strip()).replaceAll("[^A-Za-z0-9._-]", "_");
        if (result.isBlank() || ".".equals(result) || "..".equals(result)) {
            return fallback;
        }
        return result.substring(0, Math.min(result.length(), 180));
    }

    private String safeDisplayFileName(String value, String fallback) {
        String result = (value == null ? "" : value.strip())
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("[. ]+$", "");
        if (result.isBlank() || ".".equals(result) || "..".equals(result)) {
            return fallback;
        }
        return result.substring(0, Math.min(result.length(), 180));
    }

    private String extension(String fileName, String format) {
        int index = fileName.lastIndexOf('.');
        if (index >= 0 && index < fileName.length() - 1) {
            String value = fileName.substring(index).replaceAll("[^A-Za-z0-9.]", "");
            if (!value.isBlank()) {
                return value.toLowerCase(java.util.Locale.ROOT);
            }
        }
        return "." + format.toLowerCase(java.util.Locale.ROOT);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    public record StoredArtifact(String storageKey, String fileName, String contentType,
                                 long sizeBytes, String sha256) {
    }
}
