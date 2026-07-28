package com.example.spring.wechat.email.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailPropertiesTests {

    @Test
    void appliesQqDefaultsAndNormalizesValues() {
        EmailProperties properties = new EmailProperties(
                true,
                " ",
                new EmailProperties.Smtp(
                        " ",
                        0,
                        true,
                        " user@qq.com ",
                        " auth-code ",
                        " ",
                        0),
                " A@EXAMPLE.com, b@example.com ,, ",
                true,
                0,
                0);

        assertThat(properties.provider()).isEqualTo("qq");
        assertThat(properties.smtp().host()).isEqualTo("smtp.qq.com");
        assertThat(properties.smtp().port()).isEqualTo(465);
        assertThat(properties.smtp().username()).isEqualTo("user@qq.com");
        assertThat(properties.smtp().password()).isEqualTo("auth-code");
        assertThat(properties.fromAddress()).isEqualTo("user@qq.com");
        assertThat(properties.smtp().timeoutMs()).isEqualTo(15_000);
        assertThat(properties.pendingDraftTtlMinutes()).isEqualTo(10);
        assertThat(properties.maxBodyChars()).isEqualTo(8_000);
        assertThat(properties.allowedRecipientSet()).containsExactlyInAnyOrder("a@example.com", "b@example.com");
    }

    @Test
    void matchesWhitelistCaseInsensitivelyAfterTrimming() {
        EmailProperties properties = new EmailProperties(
                true,
                "qq",
                new EmailProperties.Smtp(
                        "smtp.qq.com",
                        465,
                        true,
                        "sender@qq.com",
                        "auth-code",
                        "sender@qq.com",
                        15_000),
                " Friend@Example.com ",
                true,
                10,
                8_000);

        assertThat(properties.isAllowedRecipient(" friend@example.COM ")).isTrue();
        assertThat(properties.isAllowedRecipient("other@example.com")).isFalse();
        assertThat(properties.isAllowedRecipient(" ")).isFalse();
    }
}
