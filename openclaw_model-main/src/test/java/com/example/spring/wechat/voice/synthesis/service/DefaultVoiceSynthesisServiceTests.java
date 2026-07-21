package com.example.spring.wechat.voice.synthesis.service;

import com.example.spring.wechat.voice.recognition.audio.AudioTranscoder;
import com.example.spring.wechat.voice.synthesis.client.VoiceSynthesisClient;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisAudio;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisRequest;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisSegment;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultVoiceSynthesisServiceTests {

    @Test
    void splitsLongTextBeforeSynthesisSoEveryWechatVoiceSegmentStaysUnderLimit() {
        RecordingVoiceSynthesisClient client = new RecordingVoiceSynthesisClient("silk");
        AudioTranscoder transcoder = new NoopAudioTranscoder();
        DefaultVoiceSynthesisService service = new DefaultVoiceSynthesisService(
                client,
                transcoder,
                58_000,
                16000,
                3.0);

        List<VoiceSynthesisSegment> segments = service.synthesizeForWechat("这是一段很长的语音回复。".repeat(80));

        assertThat(segments).hasSizeGreaterThan(1);
        assertThat(segments).allSatisfy(segment -> {
            assertThat(segment.durationMs()).isLessThanOrEqualTo(58_000);
            assertThat(segment.format()).isEqualTo("silk");
            assertThat(segment.fileName()).endsWith(".silk");
            assertThat(segment.sampleRate()).isEqualTo(16000);
        });
        assertThat(client.requests).hasSize(segments.size());
    }

    @Test
    void transcodesProviderAudioToSilkBeforeReturningWechatSegments() {
        RecordingVoiceSynthesisClient client = new RecordingVoiceSynthesisClient("wav");
        AudioTranscoder transcoder = new PrefixingAudioTranscoder();
        DefaultVoiceSynthesisService service = new DefaultVoiceSynthesisService(
                client,
                transcoder,
                58_000,
                16000,
                4.0);

        List<VoiceSynthesisSegment> segments = service.synthesizeForWechat("用语音回复我：你好。");

        assertThat(segments).singleElement()
                .satisfies(segment -> {
                    assertThat(new String(segment.audioBytes(), StandardCharsets.UTF_8)).startsWith("SILK:");
                    assertThat(segment.format()).isEqualTo("silk");
                    assertThat(segment.encodeType()).isEqualTo(6);
                    assertThat(segment.bitsPerSample()).isEqualTo(16);
                });
    }

    @Test
    void fallsBackToProviderAudioWhenSilkTranscodingIsNotAvailable() {
        RecordingVoiceSynthesisClient client = new RecordingVoiceSynthesisClient("mp3");
        AudioTranscoder transcoder = new FailingSilkAudioTranscoder();
        DefaultVoiceSynthesisService service = new DefaultVoiceSynthesisService(
                client,
                transcoder,
                58_000,
                16000,
                4.0);

        List<VoiceSynthesisSegment> segments = service.synthesizeForWechat("请用语音回复我。");

        assertThat(segments).singleElement()
                .satisfies(segment -> {
                    assertThat(segment.format()).isEqualTo("mp3");
                    assertThat(segment.fileName()).endsWith(".mp3");
                    assertThat(new String(segment.audioBytes(), StandardCharsets.UTF_8)).startsWith("MP3:");
                });
    }

    @Test
    void usesExplicitVoiceWhenCallerProvidesUserVoicePreference() {
        RecordingVoiceSynthesisClient client = new RecordingVoiceSynthesisClient("silk");
        AudioTranscoder transcoder = new NoopAudioTranscoder();
        DefaultVoiceSynthesisService service = new DefaultVoiceSynthesisService(
                client,
                transcoder,
                58_000,
                16000,
                4.0);

        service.synthesizeForWechat("你好，我会用新的音色回复你。", "Serena");

        assertThat(client.requests).singleElement()
                .extracting(VoiceSynthesisRequest::voice)
                .isEqualTo("Serena");
    }

    private static final class RecordingVoiceSynthesisClient implements VoiceSynthesisClient {

        private final String outputFormat;
        private final java.util.ArrayList<VoiceSynthesisRequest> requests = new java.util.ArrayList<>();

        private RecordingVoiceSynthesisClient(String outputFormat) {
            this.outputFormat = outputFormat;
        }

        @Override
        public VoiceSynthesisAudio synthesize(VoiceSynthesisRequest request) {
            requests.add(request);
            byte[] bytes = (outputFormat.toUpperCase() + ":" + request.text()).getBytes(StandardCharsets.UTF_8);
            return new VoiceSynthesisAudio(bytes, outputFormat, "audio/" + outputFormat, 16000);
        }
    }

    private static final class NoopAudioTranscoder implements AudioTranscoder {

        @Override
        public byte[] convertToWav(byte[] inputBytes, String inputFormat, Integer sampleRate) {
            return inputBytes;
        }

        @Override
        public byte[] convertToSilk(byte[] inputBytes, String inputFormat, Integer sampleRate) {
            return inputBytes;
        }
    }

    private static final class PrefixingAudioTranscoder implements AudioTranscoder {

        @Override
        public byte[] convertToWav(byte[] inputBytes, String inputFormat, Integer sampleRate) {
            return inputBytes;
        }

        @Override
        public byte[] convertToSilk(byte[] inputBytes, String inputFormat, Integer sampleRate) {
            return ("SILK:" + new String(inputBytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final class FailingSilkAudioTranscoder implements AudioTranscoder {

        @Override
        public byte[] convertToWav(byte[] inputBytes, String inputFormat, Integer sampleRate) {
            return inputBytes;
        }

        @Override
        public byte[] convertToSilk(byte[] inputBytes, String inputFormat, Integer sampleRate) {
            throw new RuntimeException("ffmpeg does not support silk");
        }
    }
}
