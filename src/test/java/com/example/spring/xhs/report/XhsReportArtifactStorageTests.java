package com.example.spring.xhs.report;

import com.example.spring.xhs.config.XhsScheduledReportProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XhsReportArtifactStorageTests {

    @TempDir
    Path tempDir;

    @Test
    void storesReadsDeletesAndRejectsPathsOutsideRoot() {
        XhsReportArtifactStorage storage = new XhsReportArtifactStorage(new XhsScheduledReportProperties(
                true, tempDir.toString(), Duration.ofSeconds(10), Duration.ofMinutes(15),
                Duration.ofMinutes(10), 3, 30));

        XhsReportArtifactStorage.StoredArtifact stored = storage.store(
                9, "brand/a", "DOCX", "小米汽车-小红书舆情分析报告.docx",
                "application/test", "content".getBytes());

        assertThat(storage.read(stored.storageKey())).isEqualTo("content".getBytes());
        assertThat(stored.fileName()).isEqualTo("小米汽车-小红书舆情分析报告.docx");
        assertThat(stored.storageKey()).doesNotContain("小米汽车");
        assertThatThrownBy(() -> storage.read("../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超出存储目录");
        storage.delete(stored.storageKey());
        assertThat(Files.exists(tempDir.resolve(stored.storageKey()))).isFalse();
    }
}
