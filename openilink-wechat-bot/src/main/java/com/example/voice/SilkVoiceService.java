package com.example.voice;

import com.example.tts.TextToSpeechService;
import io.github.kasukusakura.silkcodec.SilkCoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

public class SilkVoiceService {

    private static final Logger log = LoggerFactory.getLogger(SilkVoiceService.class);

    public static final int WECHAT_SILK_ENCODE_TYPE = 4;
    public static final int WECHAT_BITS_PER_SAMPLE = 16;
    public static final int WECHAT_DEFAULT_SAMPLE_RATE = 16000;

    public SilkResult encode(TextToSpeechService.TtsResult ttsResult) {
        if (ttsResult == null || !ttsResult.isSuccess()) {
            return SilkResult.failure(ttsResult == null ? "TTS result is empty" : ttsResult.getMessage());
        }
        try {
            String sourceFormat = normalizeSourceFormat(ttsResult.getResponseFormat());
            int sampleRate = ttsResult.getSampleRate() > 0 ? ttsResult.getSampleRate() : WECHAT_DEFAULT_SAMPLE_RATE;
            byte[] pcmBytes = toPcmBytes(ttsResult.getAudioBytes(), sourceFormat, sampleRate);
            if (pcmBytes.length == 0) {
                return SilkResult.failure("PCM audio is empty");
            }

            ByteArrayOutputStream silkOutput = new ByteArrayOutputStream();
            SilkCoder.encode(new ByteArrayInputStream(pcmBytes), silkOutput, sampleRate);
            byte[] silkBytes = silkOutput.toByteArray();
            if (silkBytes.length == 0) {
                return SilkResult.failure("SILK encoder returned empty audio");
            }

            int playTime = ttsResult.getPlayTimeSeconds() != null && ttsResult.getPlayTimeSeconds() > 0
                    ? ttsResult.getPlayTimeSeconds()
                    : estimatePcmPlayTimeSeconds(pcmBytes, sampleRate);
            log.debug("SILK V3 encoded: sourceFormat={}, pcmBytes={}, silkBytes={}, sampleRate={}, playTime={}",
                    sourceFormat, pcmBytes.length, silkBytes.length, sampleRate, playTime);
            return SilkResult.success(silkBytes, sampleRate, playTime);
        } catch (Exception e) {
            log.warn("SILK V3 encode failed: {}", e.getMessage());
            return SilkResult.failure(e.getMessage());
        }
    }

    /** Converts TTS audio to a standard PCM WAV attachment. */
    public byte[] toWavBytes(TextToSpeechService.TtsResult ttsResult) {
        if (ttsResult == null || !ttsResult.isSuccess()) {
            return new byte[0];
        }
        try {
            String sourceFormat = normalizeSourceFormat(ttsResult.getResponseFormat());
            int sampleRate = ttsResult.getSampleRate() > 0
                    ? ttsResult.getSampleRate()
                    : WECHAT_DEFAULT_SAMPLE_RATE;
            byte[] pcmBytes = toPcmBytes(ttsResult.getAudioBytes(), sourceFormat, sampleRate);
            if (pcmBytes.length == 0) {
                return new byte[0];
            }

            ByteArrayOutputStream wav = new ByteArrayOutputStream(44 + pcmBytes.length);
            writeAscii(wav, "RIFF");
            writeLittleEndianInt(wav, 36 + pcmBytes.length);
            writeAscii(wav, "WAVE");
            writeAscii(wav, "fmt ");
            writeLittleEndianInt(wav, 16);
            writeLittleEndianShort(wav, (short) 1);
            writeLittleEndianShort(wav, (short) 1);
            writeLittleEndianInt(wav, sampleRate);
            writeLittleEndianInt(wav, sampleRate * 2);
            writeLittleEndianShort(wav, (short) 2);
            writeLittleEndianShort(wav, (short) 16);
            writeAscii(wav, "data");
            writeLittleEndianInt(wav, pcmBytes.length);
            wav.write(pcmBytes);
            return wav.toByteArray();
        } catch (Exception e) {
            log.warn("WAV 音频生成失败: {}", e.getMessage());
            return new byte[0];
        }
    }

    private void writeAscii(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private void writeLittleEndianInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }

    private void writeLittleEndianShort(ByteArrayOutputStream out, short value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }

    private byte[] toPcmBytes(byte[] audioBytes, String sourceFormat, int sampleRate)
            throws IOException, UnsupportedAudioFileException {
        if (audioBytes == null || audioBytes.length == 0) {
            return new byte[0];
        }
        if ("pcm".equals(sourceFormat)) {
            return audioBytes;
        }
        if (!"wav".equals(sourceFormat)) {
            throw new IOException("Unsupported TTS source format for SILK encoding: " + sourceFormat);
        }

        try (AudioInputStream source = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioBytes))) {
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    1,
                    2,
                    sampleRate,
                    false
            );
            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(targetFormat, source)) {
                return pcmStream.readAllBytes();
            }
        }
    }

    private String normalizeSourceFormat(String sourceFormat) {
        if (sourceFormat == null || sourceFormat.isBlank()) {
            return "pcm";
        }
        return sourceFormat.trim().toLowerCase(Locale.ROOT);
    }

    private int estimatePcmPlayTimeSeconds(byte[] pcmBytes, int sampleRate) {
        if (pcmBytes == null || pcmBytes.length == 0 || sampleRate <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(pcmBytes.length / (sampleRate * 2.0)));
    }

    public static class SilkResult {
        private final boolean success;
        private final String message;
        private final byte[] audioBytes;
        private final int sampleRate;
        private final int playTimeSeconds;

        private SilkResult(boolean success, String message, byte[] audioBytes, int sampleRate, int playTimeSeconds) {
            this.success = success;
            this.message = message;
            this.audioBytes = audioBytes;
            this.sampleRate = sampleRate;
            this.playTimeSeconds = playTimeSeconds;
        }

        public static SilkResult success(byte[] audioBytes, int sampleRate, int playTimeSeconds) {
            return new SilkResult(true, "ok", audioBytes, sampleRate, playTimeSeconds);
        }

        public static SilkResult failure(String message) {
            return new SilkResult(false, message, null, 0, 0);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public byte[] getAudioBytes() {
            return audioBytes;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public int getPlayTimeSeconds() {
            return playTimeSeconds;
        }
    }
}
