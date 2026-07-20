package com.example.spring.speech;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AudioFormatDetectorTests {
    @Test
    void detectsCommonHeaders() {
        assertThat(AudioFormatDetector.detect(bytes("RIFF1234WAVE"), null)).isEqualTo("wav");
        assertThat(AudioFormatDetector.detect(bytes("#!SILK_V3\n"), null)).isEqualTo("silk");
        assertThat(AudioFormatDetector.detect(bytes("OggSdata"), null)).isEqualTo("ogg");
        assertThat(AudioFormatDetector.detect(bytes("ID3data"), null)).isEqualTo("mp3");
        assertThat(AudioFormatDetector.detect(bytes("AMRdata"), null)).isEqualTo("amr");
    }

    @Test
    void usesWechatEncodeTypeAsSilkFallback() {
        assertThat(AudioFormatDetector.detect(new byte[] {1, 2, 3}, 6)).isEqualTo("silk");
        assertThat(AudioFormatDetector.detect(new byte[] {1, 2, 3}, 1)).isEqualTo("unknown");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
