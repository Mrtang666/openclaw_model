package com.example.spring.bailian;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SecretConfigurationTests {
    @Test
    void applicationConfigurationUsesExternalSecretPlaceholders() throws Exception {
        String properties;
        try (var input = getClass().getResourceAsStream("/application.properties")) {
            assertThat(input).isNotNull();
            properties = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(properties)
            .contains("bailian.api-key=${BAILIAN_API_KEY:${DASHSCOPE_API_KEY:}}")
            .contains("weather.api-key=${WEATHER_API_KEY:}")
            .contains("optional:file:.evn[.properties]")
            .contains("optional:file:../.evn[.properties]")
            .contains("optional:file:./openclaw_model-main/.evn[.properties]");
        assertThat(properties).doesNotContain("replace_with_your");
    }
}
