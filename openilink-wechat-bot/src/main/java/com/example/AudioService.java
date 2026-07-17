package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

public class AudioService {

    private static final Logger log = LoggerFactory.getLogger(AudioService.class);

    private final String apiKey;
    private final String transcriptionUrl;
    private final String sttModel;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final String TEMP_DIR = "downloads/temp_audio";

    public AudioService() {
        LocalLLMService.Config cfg = LocalLLMService.getConfig();
        this.apiKey = cfg.getApiKey();
        this.transcriptionUrl = cfg.getTranscriptionUrl();
        this.sttModel = cfg.getSttModel();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 主入口：语音识别
     * 策略：
     *   1. 如果 ffmpeg 可用，将 .silk 转码为 .wav (16kHz mono PCM) 再识别——这识别率最高
     *   2. 直接提交 .silk + audio/silk MIME
     *   3. 直接提交 .wav MIME
     *   4. 直接提交 .mp3 MIME
     */
    public String transcribe(byte[] audioData, String fileName) {
        // 1) 尝试 ffmpeg 转码
        if (checkFfmpeg()) {
            String result = transcodeAndTranscribe(audioData, fileName);
            if (result != null) return result;
        } else {
            log.info("ffmpeg 未安装，跳过转码");
        }

        // 2) 直接提交各种格式
        String[][] attempts = {
                {"silk", "audio/silk"},
                {"wav",  "audio/wav"},
                {"mp3",  "audio/mpeg"}
        };
        for (String[] att : attempts) {
            try {
                String result = callSTT(audioData, fileName, att[0], att[1]);
                if (result != null) return result;
            } catch (Exception e) {
                log.warn("STT 调用失败 ext={}: {}", att[0], e.getMessage());
            }
        }

        log.warn("所有语音识别尝试均失败: file={}, size={} bytes", fileName, audioData.length);
        return null;
    }

    /**
     * 检查 ffmpeg 是否安装
     */
    private boolean checkFfmpeg() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"ffmpeg", "-version"});
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 用 ffmpeg 转码后提交 STT
     */
    private String transcodeAndTranscribe(byte[] audioData, String originalName) {
        try {
            Files.createDirectories(Paths.get(TEMP_DIR));
            String baseName = UUID.randomUUID().toString();
            Path inPath = Paths.get(TEMP_DIR, baseName + ".silk");
            Files.write(inPath, audioData);

            // 尝试转码为 PCM WAV (16kHz mono) - SenseVoice 最佳格式
            Path wavPath = Paths.get(TEMP_DIR, baseName + ".wav");
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", inPath.toAbsolutePath().toString(),
                    "-ar", "16000", "-ac", "1", "-sample_fmt", "s16",
                    wavPath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            int code = p.waitFor();

            // 清理输入文件
            try { Files.deleteIfExists(inPath); } catch (Exception ignored) {}

            if (code == 0 && Files.exists(wavPath) && Files.size(wavPath) > 100) {
                byte[] wavData = Files.readAllBytes(wavPath);
                log.info("ffmpeg 转码 wav 成功: {} bytes", wavData.length);
                String result = callSTT(wavData, originalName.replaceAll("\\.[^.]+$", "") + ".wav", "wav", "audio/wav");
                try { Files.deleteIfExists(wavPath); } catch (Exception ignored) {}
                if (result != null) return result;
            } else {
                log.warn("ffmpeg silk->wav 转码失败: exit={} out={}", code, out.length() > 200 ? out.substring(0,200) : out);
            }

            // 如果 silk 转码 wav 失败，试试 mp3
            Path mp3Path = Paths.get(TEMP_DIR, baseName + ".mp3");
            pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", inPath.toAbsolutePath().toString(),
                    "-acodec", "libmp3lame", "-ar", "16000", "-ac", "1",
                    mp3Path.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            p = pb.start();
            out = new String(p.getInputStream().readAllBytes());
            code = p.waitFor();

            if (code == 0 && Files.exists(mp3Path) && Files.size(mp3Path) > 100) {
                byte[] mp3Data = Files.readAllBytes(mp3Path);
                log.info("ffmpeg 转码 mp3 成功: {} bytes", mp3Data.length);
                String result = callSTT(mp3Data, originalName.replaceAll("\\.[^.]+$", "") + ".mp3", "mp3", "audio/mpeg");
                try { Files.deleteIfExists(mp3Path); } catch (Exception ignored) {}
                if (result != null) return result;
            }

        } catch (Exception e) {
            log.warn("ffmpeg 转码异常: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 直接调用 SiliconFlow /v1/audio/transcriptions
     * OpenAI 兼容格式的 multipart/form-data
     */
    private String callSTT(byte[] audioData, String originalName, String ext, String mimeType) throws Exception {
        String boundary = "Boundary-" + UUID.randomUUID();
        String fakeName = originalName.replaceAll("\\.[^.]+$", "") + "." + ext;

        byte[] body = buildMultipartBody(audioData, fakeName, mimeType, boundary);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(transcriptionUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String respBody = response.body().trim();
            log.info("STT 响应 (ext={}): {}", ext, respBody.length() > 200 ? respBody.substring(0, 200) : respBody);

            // 兼容 JSON 和纯文本两种返回格式
            String text;
            if (respBody.startsWith("{") || respBody.startsWith("[")) {
                var root = objectMapper.readTree(respBody);
                text = root.path("text").asText(null);
                if (text == null || text.isEmpty()) {
                    text = root.path("transcript").asText(null);
                }
                if (text == null) text = root.asText();
            } else {
                text = respBody;
            }

            if (text != null && !text.isBlank()) {
                log.info("语音识别成功 ext={}: {}", ext, text);
                return text;
            }
        } else {
            String respBody = response.body();
            log.warn("STT 失败 ext={}: status={}, body={}", ext, response.statusCode(),
                    respBody != null ? (respBody.length() > 300 ? respBody.substring(0, 300) : respBody) : "(empty)");
        }
        return null;
    }

    /**
     * 构造 multipart/form-data body
     * 使用 OpenAI 兼容格式：file, model, language, response_format
     */
    private byte[] buildMultipartBody(byte[] audioData, String fileName, String mimeType, String boundary) throws IOException {
        var bos = new ByteArrayOutputStream();
        String nl = "\r\n";

        // model
        writeField(bos, boundary, "model", sttModel, nl);
        // language
        writeField(bos, boundary, "language", "zh", nl);
        // response_format
        writeField(bos, boundary, "response_format", "json", nl);
        // temperature (add for better results)
        writeField(bos, boundary, "temperature", "0", nl);

        // file
        bos.write(("--" + boundary + nl).getBytes());
        bos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + nl).getBytes());
        bos.write(("Content-Type: " + mimeType + nl).getBytes());
        bos.write(nl.getBytes());
        bos.write(audioData);
        bos.write(nl.getBytes());

        // closing
        bos.write(("--" + boundary + "--" + nl).getBytes());
        return bos.toByteArray();
    }

    private void writeField(ByteArrayOutputStream bos, String boundary, String name, String value, String nl) throws IOException {
        bos.write(("--" + boundary + nl).getBytes());
        bos.write(("Content-Disposition: form-data; name=\"" + name + "\"" + nl).getBytes());
        bos.write(nl.getBytes());
        bos.write((value + nl).getBytes());
    }
}
