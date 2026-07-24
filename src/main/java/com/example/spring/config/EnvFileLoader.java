package com.example.spring.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 轻量级 .env 文件加载器。
 *
 * <p>Spring Boot 默认不会自动读取项目根目录的 .env 文件。这个类会在应用启动前读取 .env，
 * 并把其中的 KEY=VALUE 写入 Java System Properties，让 application.properties
 * 里的 ${KEY:默认值} 可以正常取到本地配置。</p>
 */
public final class EnvFileLoader {

    private EnvFileLoader() {
    }

    /**
     * 从当前工作目录读取默认的 .env 文件。
     */
    public static void loadDefault() {
        load(Path.of(".env"));
    }

    /**
     * 读取指定 .env 文件，并把未被系统环境变量覆盖的配置写入 System Properties。
     */
    public static void load(Path envFile) {
        if (envFile == null || !Files.exists(envFile)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(envFile);
            for (String line : lines) {
                loadLine(line);
            }
        } catch (IOException ignored) {
            // .env 只是本地便捷配置文件；读取失败时交给 Spring 的默认配置和环境变量继续处理。
        }
    }

    private static void loadLine(String rawLine) {
        String line = stripBom(rawLine).strip();
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        int separatorIndex = line.indexOf('=');
        if (separatorIndex <= 0) {
            return;
        }
        String key = line.substring(0, separatorIndex).strip();
        String value = normalizeValue(line.substring(separatorIndex + 1).strip());
        if (key.isEmpty() || System.getenv().containsKey(key)) {
            return;
        }
        System.setProperty(key, value);
    }

    private static String normalizeValue(String value) {
        if (value.length() >= 2) {
            boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
            boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
            if (doubleQuoted || singleQuoted) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String stripBom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
