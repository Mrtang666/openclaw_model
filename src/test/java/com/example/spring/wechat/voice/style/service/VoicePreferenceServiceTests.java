package com.example.spring.wechat.voice.style.service;

import com.example.spring.wechat.voice.style.model.VoiceCandidatePage;
import com.example.spring.wechat.voice.style.model.VoiceProfile;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VoicePreferenceServiceTests {

    @Test
    void filtersOfficialVoicesByNaturalUserPreferenceAndKeepsCandidatePage() {
        VoicePreferenceService service = new VoicePreferenceService(new VoiceCatalog(), fixedClock("2026-07-20T09:00:00Z"));

        VoiceCandidatePage page = service.searchAndRememberCandidates("user-1", "换一个温柔的女声");

        assertThat(page.candidates()).hasSizeBetween(1, 5);
        assertThat(page.candidates())
                .extracting(VoiceProfile::voice)
                .containsAnyOf("Serena", "Maia", "Mia", "Cherry");
        assertThat(page.hasMore()).isTrue();
        assertThat(service.candidateByOrdinal("user-1", 1)).isPresent();
    }

    @Test
    void keepsExplicitFemaleGenderAsHardFilterEvenWhenMaleVoiceMatchesStyle() {
        VoiceCatalog catalog = new VoiceCatalog();

        assertThat(catalog.search("柔和女声"))
                .isNotEmpty()
                .allMatch(profile -> "女声".equals(profile.gender()));
    }

    @Test
    void treatsNaturalGenderWordsAsHardFilters() {
        VoiceCatalog catalog = new VoiceCatalog();

        assertThat(catalog.search("柔和女的"))
                .isNotEmpty()
                .allMatch(profile -> "女声".equals(profile.gender()));
        assertThat(catalog.search("自然男的"))
                .isNotEmpty()
                .allMatch(profile -> "男声".equals(profile.gender()));
    }

    @Test
    void savesSelectedVoiceAndUsesCherryWhenUserHasNoPreference() {
        VoicePreferenceService service = new VoicePreferenceService(new VoiceCatalog(), fixedClock("2026-07-20T09:00:00Z"));

        assertThat(service.effectiveVoice("unknown")).isEqualTo("Cherry");

        VoiceProfile serena = service.catalog().findByVoice("Serena").orElseThrow();
        service.savePreference("user-1", serena);

        assertThat(service.effectiveVoice("user-1")).isEqualTo("Serena");
        assertThat(service.currentPreference("user-1")).contains(serena);
    }

    @Test
    void recentPreviewCanBeConfirmedOnlyWithinFiveMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T09:00:00Z"));
        VoicePreferenceService service = new VoicePreferenceService(new VoiceCatalog(), clock);
        VoiceProfile serena = service.catalog().findByVoice("Serena").orElseThrow();

        service.rememberPreview("user-1", serena);
        assertThat(service.recentPreview("user-1")).contains(serena);

        clock.instant = Instant.parse("2026-07-20T09:04:59Z");
        assertThat(service.recentPreview("user-1")).contains(serena);

        clock.instant = Instant.parse("2026-07-20T09:05:01Z");
        assertThat(service.recentPreview("user-1")).isEmpty();
    }

    private static Clock fixedClock(String value) {
        return Clock.fixed(Instant.parse(value), ZoneId.of("UTC"));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
