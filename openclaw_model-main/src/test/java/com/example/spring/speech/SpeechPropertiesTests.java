package com.example.spring.speech;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SpeechPropertiesTests {
    @Test
    void usesConservativeVoiceSendAndRetryDefaults() {
        SpeechProperties properties = new SpeechProperties();

        assertThat(properties.getVoiceSendInterval()).isEqualTo(Duration.ofMillis(2500));
        assertThat(properties.getVoiceRetryMaxAttempts()).isEqualTo(5);
        assertThat(properties.getVoiceRetryBaseDelay()).isEqualTo(Duration.ofMillis(1500));
        assertThat(properties.getVoiceRetryMaxDelay()).isEqualTo(Duration.ofSeconds(10));
    }
}
