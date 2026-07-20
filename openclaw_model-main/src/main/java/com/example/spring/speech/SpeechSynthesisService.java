package com.example.spring.speech;

public interface SpeechSynthesisService {
    SpeechSynthesisResult synthesize(String text)
        throws SpeechRecognitionException, InterruptedException;

    SpeechSynthesisResult synthesize(String text, String format)
        throws SpeechRecognitionException, InterruptedException;

    SpeechSynthesisResult synthesize(
        String text,
        String format,
        VoiceSynthesisOptions options)
        throws SpeechRecognitionException, InterruptedException;
}
