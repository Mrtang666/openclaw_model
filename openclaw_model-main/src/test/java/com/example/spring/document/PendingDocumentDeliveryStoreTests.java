package com.example.spring.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingDocumentDeliveryStoreTests {
    @TempDir
    Path tempDirectory;

    @Test
    void persistsAndCompletesFailedDocumentDelivery() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        properties.setDataDirectory(tempDirectory);
        PendingDocumentDeliveryStore store = new PendingDocumentDeliveryStore(properties);
        store.afterPropertiesSet();
        GeneratedDocument document = new GeneratedDocument(
            new byte[] {1, 2, 3}, "application/pdf", "result.pdf", "PDF 文档");

        assertThat(store.save("user", 8L, document)).isTrue();
        PendingDocumentDelivery pending = store.loadPending().get(0);
        assertThat(pending.userId()).isEqualTo("user");
        assertThat(pending.document().fileName()).isEqualTo("result.pdf");

        store.complete(pending);
        assertThat(store.loadPending()).isEmpty();
    }
}
