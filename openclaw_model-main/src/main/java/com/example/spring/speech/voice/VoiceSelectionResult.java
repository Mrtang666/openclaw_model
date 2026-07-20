package com.example.spring.speech.voice;

public record VoiceSelectionResult(
    boolean consumed,
    String reply,
    VoicePreference preview) {
    public static VoiceSelectionResult ignored() {
        return new VoiceSelectionResult(false, "", null);
    }

    public static VoiceSelectionResult replied(String reply) {
        return new VoiceSelectionResult(true, reply, null);
    }

    public static VoiceSelectionResult preview(String reply, VoicePreference preference) {
        return new VoiceSelectionResult(true, reply, preference);
    }
}
