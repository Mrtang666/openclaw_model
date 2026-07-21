package com.example.spring.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentMemoryServiceTests {
    @TempDir
    Path tempDirectory;

    @Test
    void storesAndResolvesActiveDocumentFollowUp() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        properties.setDataDirectory(tempDirectory);
        DocumentMemoryService service = new DocumentMemoryService(
            properties, new DocumentExtractor(properties));
        service.afterPropertiesSet();

        service.store("user", 10L,
            "这是需要总结的项目文件。".getBytes(StandardCharsets.UTF_8), "project.txt");

        assertThat(service.resolve("user", "1"))
            .singleElement().satisfies(document -> {
                assertThat(document.fileName()).isEqualTo("project.txt");
                assertThat(document.extractedText()).contains("需要总结");
            });
        assertThat(service.findByMessage("user", 10L)).hasSize(1);
    }
}
