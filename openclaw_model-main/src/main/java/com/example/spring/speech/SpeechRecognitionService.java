package com.example.spring.speech;

public interface SpeechRecognitionService {
    SpeechRecognitionResult recognize(VoiceAsset voice)
        throws SpeechRecognitionException, InterruptedException;
}
