package com.example.spring.wechat.voice.audio;

import com.example.spring.wechat.voice.VoiceRecognitionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class FfmpegAudioTranscoder implements AudioTranscoder {

    private final String ffmpegPath;
    private final boolean enabled;
    private final long timeoutSeconds;

    public FfmpegAudioTranscoder(
            @Value("${audio.ffmpeg.path:ffmpeg}") String ffmpegPath,
            @Value("${audio.ffmpeg.enabled:true}") boolean enabled,
            @Value("${audio.ffmpeg.timeout-seconds:20}") long timeoutSeconds) {
        this.ffmpegPath = ffmpegPath;
        this.enabled = enabled;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public byte[] convertToWav(byte[] inputBytes, String inputFormat, Integer sampleRate) {
        if (!enabled) {
            throw new VoiceRecognitionException("语音格式需要转换，但 ffmpeg 转码未启用");
        }
        if (inputBytes == null || inputBytes.length == 0) {
            throw new VoiceRecognitionException("语音格式转换失败：音频内容为空");
        }

        Path inputPath = null;
        Path outputPath = null;
        try {
            String extension = safeExtension(inputFormat);
            inputPath = Files.createTempFile("openclaw-voice-", "." + extension);
            outputPath = Files.createTempFile("openclaw-voice-", ".wav");
            Files.write(inputPath, inputBytes);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-y",
                    "-i",
                    inputPath.toString(),
                    "-ac",
                    "1",
                    "-ar",
                    String.valueOf(sampleRate == null || sampleRate <= 0 ? 16000 : sampleRate),
                    outputPath.toString());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            Process process = processBuilder.start();
            boolean finished = process.waitFor(Math.max(1L, timeoutSeconds), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new VoiceRecognitionException("语音格式转换超时，请重新发送语音");
            }
            if (process.exitValue() != 0) {
                throw new VoiceRecognitionException("语音格式转换失败，请换一种语音格式重试");
            }

            byte[] outputBytes = Files.readAllBytes(outputPath);
            if (outputBytes.length == 0) {
                throw new VoiceRecognitionException("语音格式转换失败：转换结果为空");
            }
            return outputBytes;
        } catch (VoiceRecognitionException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new VoiceRecognitionException("语音格式转换失败：无法调用 ffmpeg", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VoiceRecognitionException("语音格式转换被中断", exception);
        } finally {
            deleteQuietly(inputPath);
            deleteQuietly(outputPath);
        }
    }

    private String safeExtension(String inputFormat) {
        String normalized = inputFormat == null ? "" : inputFormat.strip().toLowerCase(Locale.ROOT);
        if (normalized.matches("[a-z0-9]{1,8}")) {
            return normalized;
        }
        return "audio";
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary file cleanup should not hide the real voice recognition error.
        }
    }
}
