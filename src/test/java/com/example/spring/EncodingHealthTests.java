package com.example.spring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EncodingHealthTests {

    private static final List<String> SOURCE_ROOTS = List.of(
            "src/main/java",
            "src/test/java",
            "src/main/resources");

    private static final List<String> MOJIBAKE_MARKERS = List.of(
            "\u951f\u65a4\u62f7",
            "\ufffd",
            "\u00c3",
            "\u00c2",
            "\u95bf",
            "\u9428",
            "\u9422",
            "\u7ecb",
            "\u934f",
            "\u5bf0",
            "\u7f03",
            "\u7487",
            "\u59af");

    @Test
    void projectTextFilesDoNotContainReplacementCharactersOrCommonMojibakeMarkers() throws IOException {
        List<String> offenders = SOURCE_ROOTS.stream()
                .map(Path::of)
                .filter(Files::exists)
                .flatMap(root -> textFiles(root).stream())
                .flatMap(path -> offendingLines(path).stream())
                .toList();

        assertThat(offenders).isEmpty();
    }

    private List<Path> textFiles(Path root) {
        try (var paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(this::isCheckedTextFile)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("扫描源码文件失败：" + root, exception);
        }
    }

    private boolean isCheckedTextFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java")
                || name.endsWith(".properties")
                || name.endsWith(".sql")
                || name.endsWith(".md");
    }

    private List<String> offendingLines(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return java.util.stream.IntStream.range(0, lines.size())
                    .filter(index -> containsMojibakeMarker(lines.get(index)))
                    .mapToObj(index -> path + ":" + (index + 1) + " :: " + lines.get(index).strip())
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("读取源码文件失败：" + path, exception);
        }
    }

    private static boolean containsMojibakeMarker(String line) {
        return MOJIBAKE_MARKERS.stream().anyMatch(line::contains);
    }
}
