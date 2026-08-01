package com.example.spring.xhs.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XhsAuthorKeyHasherTests {

    @Test
    void createsStableNonReversibleAuthorKey() {
        XhsAuthorKeyHasher hasher = new XhsAuthorKeyHasher("test-secret");

        String first = hasher.hash("user-123");
        String second = hasher.hash(" user-123 ");

        assertThat(first).hasSize(64).isEqualTo(second).doesNotContain("user-123");
        assertThat(hasher.hash("user-456")).isNotEqualTo(first);
    }

    @Test
    void keepsMissingAuthorAnonymous() {
        XhsAuthorKeyHasher hasher = new XhsAuthorKeyHasher("test-secret");

        assertThat(hasher.hash(null)).isEmpty();
        assertThat(hasher.hash("  ")).isEmpty();
    }
}
