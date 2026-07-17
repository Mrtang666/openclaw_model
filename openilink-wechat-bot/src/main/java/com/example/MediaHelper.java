package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MediaHelper {

    private static final Logger log = LoggerFactory.getLogger(MediaHelper.class);
    private static final String DOWNLOADS_DIR = "downloads";

    public static Path downloadAndSave(byte[] data, String userId, String msgId, String extension, String subDir) throws IOException {
        String fileName = (msgId != null ? msgId : String.valueOf(System.currentTimeMillis())) + extension;
        Path dir = Paths.get(DOWNLOADS_DIR, userId != null ? userId : "unknown", subDir);
        Files.createDirectories(dir);
        Path filePath = dir.resolve(fileName);
        Files.write(filePath, data);
        log.info("媒体已保存: {} ({} bytes)", filePath, data.length);
        return filePath;
    }
}
