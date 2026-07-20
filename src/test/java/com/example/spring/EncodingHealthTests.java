package com.example.spring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EncodingHealthTests {

    private static final List<String> MOJIBAKE_MARKERS = List.of(
            "�", "鈧", "€", "寰俊", "璇", "鍥剧", "鐢熸", "浣犳", "锛", "绋嶅", "娴佸", "撳",
            "宸ュ叿", "璋冪敤", "璁″垝", "鏈敞", "宸插彂", "閫佽", "閻", "鐢", "鍨", "顏");

    @Test
    void mainJavaSourcesDoNotContainCommonChineseMojibakeMarkers() throws IOException {
        Path sourceRoot = Path.of("src/main/java");

        List<String> offenders;
        try (var paths = Files.walk(sourceRoot)) {
            offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> offendingLines(path).stream())
                    .toList();
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    void mainJavaSourcesDoNotContainSuspiciousQuestionMarkComments() throws IOException {
        Path sourceRoot = Path.of("src/main/java");

        List<String> offenders;
        try (var paths = Files.walk(sourceRoot)) {
            offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> suspiciousQuestionMarkCommentLines(path).stream())
                    .toList();
        }

        assertThat(offenders).isEmpty();
    }

    private List<String> offendingLines(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return lines.stream()
                    .filter(EncodingHealthTests::containsMojibakeMarker)
                    .map(line -> path + " :: " + line.strip())
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("读取源码文件失败：" + path, exception);
        }
    }

    private static boolean containsMojibakeMarker(String line) {
        return MOJIBAKE_MARKERS.stream().anyMatch(line::contains);
    }

    private List<String> suspiciousQuestionMarkCommentLines(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return lines.stream()
                    .filter(EncodingHealthTests::isSuspiciousQuestionMarkComment)
                    .map(line -> path + " :: " + line.strip())
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("读取源码文件失败：" + path, exception);
        }
    }

    private static boolean isSuspiciousQuestionMarkComment(String line) {
        String trimmed = line.stripLeading();
        return (trimmed.startsWith("*") || trimmed.startsWith("//")) && trimmed.contains("???");
    }
}
