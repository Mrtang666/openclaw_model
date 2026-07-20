package com.example.spring.speech;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

final class SilkAudioDecoder {
    private SilkAudioDecoder() {
    }

    static VoiceAsset decode(VoiceAsset source, String decoderPath)
        throws SpeechRecognitionException, InterruptedException {
        if (decoderPath == null || decoderPath.isBlank()) {
            throw new SpeechRecognitionException(
                "检测到微信 SILK 语音，但未配置 SILK 解码器。请配置 SPEECH_SILK_DECODER_PATH。" );
        }
        try {
            Path directory = Files.createTempDirectory("wechat-silk-");
            Path input = directory.resolve("input.silk");
            Path output = directory.resolve("output.wav");
            Files.write(input, source.data());
            Process process = new ProcessBuilder(
                decoderPath, input.toString(), output.toString())
                .redirectErrorStream(true)
                .start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new SpeechRecognitionException("SILK 解码超时");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
                throw new SpeechRecognitionException("SILK 解码失败，退出码=" + process.exitValue());
            }
            byte[] wav = Files.readAllBytes(output);
            deleteQuietly(input);
            deleteQuietly(output);
            Files.deleteIfExists(directory);
            return new VoiceAsset(
                wav, "wav", source.sampleRate(), source.bitsPerSample(), source.durationMs());
        } catch (IOException exception) {
            throw new SpeechRecognitionException("无法执行 SILK 解码器：" + decoderPath, exception);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary audio cleanup must not hide a successful transcription.
        }
    }
}
