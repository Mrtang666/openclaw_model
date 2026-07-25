package com.example.spring.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretMaskerTests {

    @Test
    void masksApiKeyWithoutLeakingFullSecret() {
        String masked = SecretMasker.mask("sk-1234567890abcdef");

        assertThat(masked).isEqualTo("sk-1****cdef");
        assertThat(masked).doesNotContain("1234567890ab");
    }

    @Test
    void reportsMissingWhenValueIsBlank() {
        assertThat(SecretMasker.mask("   ")).isEqualTo("MISSING");
    }

    @Test
    void extractsHostFromUrlWithoutQueryOrPath() {
        assertThat(SecretMasker.host("https://example.aliyuncs.com/compatible-mode/v1?token=abc"))
                .isEqualTo("example.aliyuncs.com");
    }
}
