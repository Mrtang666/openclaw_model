package com.example.spring.speech.voice;

import com.example.spring.speech.voice.VoiceProfile.Accent;
import com.example.spring.speech.voice.VoiceProfile.Gender;
import com.example.spring.speech.voice.VoiceProfile.Language;
import com.example.spring.speech.voice.VoiceProfile.Style;

public record VoiceSelectionCriteria(
    Language language,
    Gender gender,
    Style style,
    Accent accent) {

    public VoiceSelectionCriteria withLanguage(Language value) {
        return new VoiceSelectionCriteria(value, gender, style, accent);
    }

    public VoiceSelectionCriteria withGender(Gender value) {
        return new VoiceSelectionCriteria(language, value, style, accent);
    }

    public VoiceSelectionCriteria withStyle(Style value) {
        return new VoiceSelectionCriteria(language, gender, value, accent);
    }

    public VoiceSelectionCriteria withAccent(Accent value) {
        return new VoiceSelectionCriteria(language, gender, style, value);
    }
}
