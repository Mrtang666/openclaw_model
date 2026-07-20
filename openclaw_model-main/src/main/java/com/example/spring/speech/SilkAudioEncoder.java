package com.example.spring.speech;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class SilkAudioEncoder {
    private SilkAudioEncoder() {
    }

    public static SpeechSynthesisResult encode(
        SpeechSynthesisResult source,
        String encoderPath)
        throws SpeechRecognitionException, InterruptedException {
        if (source == null || source.data() == null || source.data().length == 0) {
            throw new SpeechRecognitionException("语音合成结果为空，无法转换为微信语音");
        }
        if ("silk".equalsIgnoreCase(source.format())) {
            return source;
        }
        if (encoderPath == null || encoderPath.isBlank()) {
            throw new SpeechRecognitionException(
                "微信语音需要 SILK 格式，但未配置 SPEECH_SILK_ENCODER_PATH");
        }

        Path directory = null;
        try {
            directory = Files.createTempDirectory("wechat-silk-");
            Path input = directory.resolve("input." + source.format());
            Path output = directory.resolve("output.silk");
            Files.write(input, source.data());
            Process process = new ProcessBuilder(
                encoderCommand(encoderPath.trim(), input, output))
                .redirectErrorStream(true)
                .start();
            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            String processOutput = new String(process.getInputStream().readAllBytes());
            if (!completed) {
                process.destroyForcibly();
                throw new SpeechRecognitionException("SILK 编码器超时");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
                String detail = processOutput == null ? "" : processOutput.trim();
                if (detail.length() > 300) {
                    detail = detail.substring(0, 300);
                }
                throw new SpeechRecognitionException(
                    "SILK 编码失败" + (detail.isBlank() ? "" : "：" + detail));
            }
            byte[] silk = Files.readAllBytes(output);
            if (!containsHeader(silk, "#!SILK_V3")) {
                throw new SpeechRecognitionException("SILK 编码器输出格式无效");
            }
            return new SpeechSynthesisResult(
                silk, "silk", source.sampleRate(), source.bitsPerSample(),
                source.channels(), source.durationMs());
        } catch (IOException exception) {
            throw new SpeechRecognitionException("无法执行 SILK 编码器", exception);
        } finally {
            if (directory != null) {
                try (var files = Files.walk(directory)) {
                    files.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                                // Temporary cleanup must not hide the send result.
                            }
                        });
                } catch (IOException ignored) {
                    // Temporary cleanup is best effort.
                }
            }
        }
    }

    private static boolean containsHeader(byte[] data, String value) {
        byte[] prefix = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (data == null || data.length < prefix.length) {
            return false;
        }
        int limit = Math.min(8, data.length - prefix.length);
        for (int offset = 0; offset <= limit; offset++) {
            boolean matches = true;
            for (int index = 0; index < prefix.length; index++) {
                if (data[offset + index] != prefix[index]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static java.util.List<String> encoderCommand(
        String encoderPath,
        Path input,
        Path output) {
        String normalized = encoderPath.toLowerCase(java.util.Locale.ROOT);
        if (normalized.endsWith(".cmd") || normalized.endsWith(".bat")) {
            Path wrapper = Path.of(encoderPath).toAbsolutePath().normalize();
            Path script = wrapper.resolveSibling("silk-encoder.mjs");
            if (!Files.isRegularFile(script)) {
                return java.util.List.of(
                    "cmd.exe", "/d", "/c", wrapper.toString(),
                    input.toString(), output.toString());
            }
            return java.util.List.of(
                "node", script.toString(), input.toString(), output.toString());
        }
        return java.util.List.of(encoderPath, input.toString(), output.toString());
    }
}
