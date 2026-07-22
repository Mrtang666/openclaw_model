package com.example.speech;

import com.example.LocalLLMService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kasukusakura.silkcodec.SilkCoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

public class SpeechRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(SpeechRecognitionService.class);
    private static final String TEMP_DIR = "downloads/temp_audio";
    private static final int SILK_SAMPLE_RATE = 24000;
    private static final int PCM_CHANNELS = 1;
    private static final int PCM_BITS_PER_SAMPLE = 16;

    private final String apiKey;
    private final String transcriptionUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SpeechRecognitionService() {
        LocalLLMService.Config cfg = LocalLLMService.getConfig();
        this.apiKey = envOrDefault("LLM_API_KEY", cfg.getApiKey());
        this.transcriptionUrl = envOrDefault("LLM_TRANSCRIPTION_URL", cfg.getTranscriptionUrl());
        this.model = envOrDefault("LLM_STT_MODEL", cfg.getSttModel());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String transcribe(byte[] audioData, String originalFileName) {
        if (audioData == null || audioData.length == 0) {
            log.warn("语音识别跳过：音频内容为空");
            return null;
        }
        if (transcriptionUrl == null || transcriptionUrl.isBlank()) {
            log.warn("语音识别跳过：llm.transcription.url 未配置");
            return null;
        }

        String safeName = safeFileName(originalFileName);
        String ext = inferExtension(safeName);
        if ("silk".equals(ext)) {
            String result = decodeSilkAndTranscribe(audioData, safeName);
            if (result != null) {
                return result;
            }
            log.warn("本地 SILK 解码识别失败，跳过直接上传 SILK，等待微信语音转文字兜底");
            return null;
        }

        if (isFfmpegAvailable()) {
            String result = transcodeAndTranscribe(audioData, safeName);
            if (result != null) {
                return result;
            }
        } else {
            log.info("ffmpeg 未安装或不可用，跳过语音转码");
        }

        String mimeType = mimeTypeFor(ext);
        String result = tryDirectTranscribe(audioData, safeName, ext, mimeType);
        if (result != null) {
            return result;
        }

        if (!"silk".equals(ext)) {
            result = tryDirectTranscribe(audioData, replaceExtension(safeName, "silk"), "silk", "audio/silk");
            if (result != null) {
                return result;
            }
        }

        log.warn("语音识别失败：file={}, size={} bytes", safeName, audioData.length);
        return null;
    }

    private String decodeSilkAndTranscribe(byte[] silkData, String originalName) {
        try {
            ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
            SilkCoder.decode(new ByteArrayInputStream(silkData), pcmOut);
            byte[] pcmBytes = pcmOut.toByteArray();
            if (pcmBytes.length == 0) {
                log.warn("SILK 解码结果为空: file={}", originalName);
                return null;
            }

            byte[] wavBytes = wrapPcmAsWav(pcmBytes, SILK_SAMPLE_RATE, PCM_CHANNELS, PCM_BITS_PER_SAMPLE);
            log.info("SILK 已本地解码为 WAV 后识别: file={}, silkBytes={}, pcmBytes={}, wavBytes={}",
                    originalName, silkData.length, pcmBytes.length, wavBytes.length);
            return callTranscription(wavBytes, replaceExtension(originalName, "wav"), "audio/wav");
        } catch (Exception e) {
            log.warn("SILK 本地解码失败: file={}, error={}", originalName, e.getMessage());
            return null;
        }
    }

    private byte[] wrapPcmAsWav(byte[] pcmBytes, int sampleRate, int channels, int bitsPerSample) throws IOException {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcmBytes.length;
        int riffSize = 36 + dataSize;

        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataSize);
        writeAscii(out, "RIFF");
        writeLittleEndianInt(out, riffSize);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeLittleEndianInt(out, 16);
        writeLittleEndianShort(out, 1);
        writeLittleEndianShort(out, channels);
        writeLittleEndianInt(out, sampleRate);
        writeLittleEndianInt(out, byteRate);
        writeLittleEndianShort(out, blockAlign);
        writeLittleEndianShort(out, bitsPerSample);
        writeAscii(out, "data");
        writeLittleEndianInt(out, dataSize);
        out.write(pcmBytes);
        return out.toByteArray();
    }

    private void writeLittleEndianInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private void writeLittleEndianShort(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    private String transcodeAndTranscribe(byte[] audioData, String originalName) {
        Path inputPath = null;
        Path wavPath = null;
        Path mp3Path = null;
        try {
            Files.createDirectories(Paths.get(TEMP_DIR));
            String baseName = UUID.randomUUID().toString();
            inputPath = Paths.get(TEMP_DIR, baseName + "." + inferExtension(originalName));
            Files.write(inputPath, audioData);

            wavPath = Paths.get(TEMP_DIR, baseName + ".wav");
            if (runFfmpeg(inputPath, wavPath, "-ar", "16000", "-ac", "1", "-sample_fmt", "s16")) {
                String result = callTranscription(Files.readAllBytes(wavPath), replaceExtension(originalName, "wav"), "audio/wav");
                if (result != null) {
                    return result;
                }
            }

            mp3Path = Paths.get(TEMP_DIR, baseName + ".mp3");
            if (runFfmpeg(inputPath, mp3Path, "-acodec", "libmp3lame", "-ar", "16000", "-ac", "1")) {
                String result = callTranscription(Files.readAllBytes(mp3Path), replaceExtension(originalName, "mp3"), "audio/mpeg");
                if (result != null) {
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("语音转码识别失败：{}", e.getMessage());
        } finally {
            deleteQuietly(inputPath);
            deleteQuietly(wavPath);
            deleteQuietly(mp3Path);
        }
        return null;
    }

    private boolean runFfmpeg(Path inputPath, Path outputPath, String... outputArgs) {
        try {
            String[] command = new String[5 + outputArgs.length];
            command[0] = "ffmpeg";
            command[1] = "-y";
            command[2] = "-i";
            command[3] = inputPath.toAbsolutePath().toString();
            System.arraycopy(outputArgs, 0, command, 4, outputArgs.length);
            command[command.length - 1] = outputPath.toAbsolutePath().toString();

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode == 0 && Files.exists(outputPath) && Files.size(outputPath) > 100) {
                log.info("ffmpeg 转码成功：{} -> {}", inputPath.getFileName(), outputPath.getFileName());
                return true;
            }
            log.warn("ffmpeg 转码失败：exit={}, output={}", exitCode, abbreviate(output, 240));
        } catch (Exception e) {
            log.warn("ffmpeg 执行失败：{}", e.getMessage());
        }
        return false;
    }

    private String tryDirectTranscribe(byte[] audioData, String fileName, String ext, String mimeType) {
        try {
            return callTranscription(audioData, replaceExtension(fileName, ext), mimeType);
        } catch (Exception e) {
            log.warn("直接语音识别失败 ext={}：{}", ext, e.getMessage());
            return null;
        }
    }

    private String callTranscription(byte[] audioData, String fileName, String mimeType) throws Exception {
        String boundary = "Boundary-" + UUID.randomUUID();
        byte[] body = buildMultipartBody(audioData, fileName, mimeType, boundary);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(transcriptionUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(120));
        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String responseBody = response.body() == null ? "" : response.body().trim();

        if (response.statusCode() != 200) {
            log.warn("语音识别 API 失败：status={}, body={}", response.statusCode(), abbreviate(responseBody, 300));
            return null;
        }

        String text = extractText(responseBody);
        if (text != null && !text.isBlank()) {
            log.info("语音识别成功 model={} file={}：{}", model, fileName, text);
            return text.trim();
        }
        log.warn("语音识别 API 未返回有效文本：{}", abbreviate(responseBody, 300));
        return null;
    }

    private byte[] buildMultipartBody(byte[] audioData, String fileName, String mimeType, String boundary) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String nl = "\r\n";

        writeField(body, boundary, "model", model, nl);
        writeField(body, boundary, "language", "zh", nl);
        writeField(body, boundary, "response_format", "json", nl);
        writeField(body, boundary, "temperature", "0", nl);

        writeAscii(body, "--" + boundary + nl);
        writeAscii(body, "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + nl);
        writeAscii(body, "Content-Type: " + mimeType + nl);
        writeAscii(body, nl);
        body.write(audioData);
        writeAscii(body, nl);
        writeAscii(body, "--" + boundary + "--" + nl);
        return body.toByteArray();
    }

    private void writeField(ByteArrayOutputStream body, String boundary, String name, String value, String nl) throws IOException {
        writeAscii(body, "--" + boundary + nl);
        writeAscii(body, "Content-Disposition: form-data; name=\"" + name + "\"" + nl);
        writeAscii(body, nl);
        writeAscii(body, value + nl);
    }

    private void writeAscii(ByteArrayOutputStream body, String value) throws IOException {
        body.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private String extractText(String responseBody) throws IOException {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        if (!responseBody.startsWith("{") && !responseBody.startsWith("[")) {
            return responseBody;
        }
        JsonNode root = objectMapper.readTree(responseBody);
        String text = textAt(root, "text");
        if (text != null) return text;
        text = textAt(root, "transcript");
        if (text != null) return text;
        text = textAt(root.path("data"), "text");
        if (text != null) return text;
        text = textAt(root.path("data"), "transcript");
        if (text != null) return text;
        return root.isTextual() ? root.asText() : null;
    }

    private String textAt(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || !node.has(fieldName)) {
            return null;
        }
        String text = node.path(fieldName).asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private boolean isFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String safeFileName(String fileName) {
        String name = fileName == null || fileName.isBlank() ? "voice.silk" : fileName;
        name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
        return name.contains(".") ? name : name + ".silk";
    }

    private String inferExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (ext.matches("[a-z0-9]{1,8}")) {
                return ext;
            }
        }
        return "silk";
    }

    private String replaceExtension(String fileName, String ext) {
        int dot = fileName.lastIndexOf('.');
        String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
        return base + "." + ext;
    }

    private String mimeTypeFor(String ext) {
        switch (ext.toLowerCase(Locale.ROOT)) {
            case "wav":
                return "audio/wav";
            case "mp3":
                return "audio/mpeg";
            case "m4a":
            case "mp4":
                return "audio/mp4";
            case "amr":
                return "audio/amr";
            case "silk":
            default:
                return "audio/silk";
        }
    }

    private String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
