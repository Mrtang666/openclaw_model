package com.example.spring.wechat.voice.audio;

public interface AudioTranscoder {

    byte[] convertToWav(byte[] inputBytes, String inputFormat, Integer sampleRate);
}
