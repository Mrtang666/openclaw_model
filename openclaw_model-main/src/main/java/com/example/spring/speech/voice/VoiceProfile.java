package com.example.spring.speech.voice;

import java.util.Set;

public record VoiceProfile(
    String voiceId,
    String displayName,
    Gender gender,
    Set<Style> styles,
    Set<Language> languages,
    Accent accent,
    String description,
    int popularity) {

    public enum Gender { FEMALE, MALE, ANY }
    public enum Style { GENTLE, STEADY, LIVELY, MATURE, NATURAL, ANY }
    public enum Language { CHINESE, ENGLISH, DIALECT, ANY }
    public enum Accent {
        MANDARIN, AMERICAN, INTERNATIONAL,
        CANTONESE, SICHUAN, SHANGHAI, BEIJING, NANJING,
        SHAANXI, MINNAN, TIANJIN, ANY
    }
}
