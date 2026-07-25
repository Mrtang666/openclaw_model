package com.example.spring.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationVersionTests {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(V\\d+)__.+\\.sql$");

    @Test
    void migrationVersionsAreUnique() throws IOException {
        Path migrationDir = Path.of("src", "main", "resources", "db", "migration");
        Map<String, List<String>> filesByVersion = new LinkedHashMap<>();

        try (Stream<Path> paths = Files.list(migrationDir)) {
            paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .forEach(fileName -> {
                        Matcher matcher = VERSION_PATTERN.matcher(fileName);
                        if (matcher.matches()) {
                            filesByVersion.computeIfAbsent(matcher.group(1), ignored -> new java.util.ArrayList<>())
                                    .add(fileName);
                        }
                    });
        }

        Map<String, List<String>> duplicates = new LinkedHashMap<>();
        filesByVersion.forEach((version, files) -> {
            if (files.size() > 1) {
                duplicates.put(version, files);
            }
        });

        assertThat(duplicates)
                .as("Flyway migration version must be unique")
                .isEmpty();
    }

    @Test
    void migrationFilesUseLfLineEndings() throws IOException {
        Path migrationDir = Path.of("src", "main", "resources", "db", "migration");

        try (Stream<Path> paths = Files.list(migrationDir)) {
            paths.filter(path -> path.getFileName().toString().matches("^V\\d+__.+\\.sql$"))
                    .forEach(path -> {
                        try {
                            assertThat(Files.readString(path))
                                    .as("Migration %s must use LF line endings", path)
                                    .doesNotContain("\r");
                        } catch (IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    });
        }
    }
}
