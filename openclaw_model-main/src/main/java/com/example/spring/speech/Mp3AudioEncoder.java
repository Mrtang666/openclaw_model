package com.example.spring.speech;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class Mp3AudioEncoder {
    private Mp3AudioEncoder() {
    }

    public static SpeechSynthesisResult encode(
        SpeechSynthesisResult source,
        String silkEncoderPath)
        throws SpeechRecognitionException, InterruptedException {
        if (source == null || source.data() == null || source.data().length == 0) {
            throw new SpeechRecognitionException("语音合成结果为空，无法生成 MP3");
        }
        Path wrapper = Path.of(silkEncoderPath).toAbsolutePath().normalize();
        Path script = wrapper.resolveSibling("wav-to-mp3.mjs");
        if (!Files.isRegularFile(script)) {
            throw new SpeechRecognitionException("缺少 WAV 转 MP3 工具：" + script);
        }
        Path directory = null;
        try {
            directory = Files.createTempDirectory("wechat-mp3-");
            Path input = directory.resolve("input.wav");
            Path output = directory.resolve("output.mp3");
            Files.write(input, source.data());
            Process process = new ProcessBuilder(
                List.of("node", script.toString(), input.toString(), output.toString()))
                .redirectErrorStream(true)
                .start();
            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            String processOutput = new String(process.getInputStream().readAllBytes());
            if (!completed) {
                process.destroyForcibly();
                throw new SpeechRecognitionException("MP3 编码器超时");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
                throw new SpeechRecognitionException(
                    "MP3 编码失败" + (processOutput.isBlank() ? "" : "：" + processOutput.trim()));
            }
            byte[] mp3 = Files.readAllBytes(output);
            if (!isMp3(mp3)) {
                throw new SpeechRecognitionException("MP3 编码器输出格式无效");
            }
            return new SpeechSynthesisResult(
                mp3, "mp3", source.sampleRate(), source.bitsPerSample(),
                source.channels(), source.durationMs());
        } catch (IOException exception) {
            throw new SpeechRecognitionException("无法执行 MP3 编码器", exception);
        } finally {
            if (directory != null) {
                try (var files = Files.walk(directory)) {
                    files.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Temporary cleanup is best effort.
                        }
                    });
                } catch (IOException ignored) {
                    // Temporary cleanup is best effort.
                }
            }
        }
    }

    private static boolean isMp3(byte[] data) {
        return data != null && data.length >= 3
            && ((data[0] == 'I' && data[1] == 'D' && data[2] == '3')
                || ((data[0] & 0xFF) == 0xFF && (data[1] & 0xE0) == 0xE0));
    }
}
