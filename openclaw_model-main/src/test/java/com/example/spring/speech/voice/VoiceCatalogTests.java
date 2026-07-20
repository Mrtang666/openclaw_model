package com.example.spring.speech.voice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.speech.voice.VoiceProfile.Gender;
import com.example.spring.speech.voice.VoiceProfile.Language;
import com.example.spring.speech.voice.VoiceProfile.Style;
import org.junit.jupiter.api.Test;

class VoiceCatalogTests {
    private final VoiceCatalog catalog = new VoiceCatalog();

    @Test
    void returnsAtMostTenVoicesInPopularityOrder() {
        var voices = catalog.recommend(new VoiceSelectionCriteria(
            Language.CHINESE, Gender.ANY, Style.ANY, null));

        assertThat(voices).hasSize(10);
        assertThat(voices).extracting(VoiceProfile::voiceId)
            .startsWith("Cherry", "Serena", "Ethan");
    }

    @Test
    void filtersDialectAndGender() {
        var voices = catalog.recommend(new VoiceSelectionCriteria(
            Language.DIALECT, Gender.FEMALE, Style.ANY, null));

        assertThat(voices).extracting(VoiceProfile::voiceId)
            .containsExactly("Jada", "Sunny", "Kiki");
    }
}
