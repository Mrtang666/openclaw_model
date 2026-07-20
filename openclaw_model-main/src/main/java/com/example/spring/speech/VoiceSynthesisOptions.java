package com.example.spring.speech;

public record VoiceSynthesisOptions(String voiceId, String languageType) {
    public static VoiceSynthesisOptions defaults(SpeechProperties properties) {
        return new VoiceSynthesisOptions(
            properties.getTtsVoice(), properties.getTtsLanguageType());
    }
}
