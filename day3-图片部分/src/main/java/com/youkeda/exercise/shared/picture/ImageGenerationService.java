package com.youkeda.exercise.shared.picture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class ImageGenerationService {

    private static final String API_KEY = "sk-ws-H.EDYDLLX.QWwS.MEYCIQCfGazv9lfSewDUwISsGzX9p1FJ1a9lgvSwmBaOmTSxZgIhAPNRqkix9kzct-4NpoHhmgxCwrwTi2EpImGGmHM1mSEH";
    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] generateImage(String prompt) throws Exception {
        // 1. 构建正确的请求体 (使用 messages 数组)
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", "wan2.6-t2i");  // 改用官方推荐的模型

        ObjectNode input = root.putObject("input");
        ArrayNode messages = input.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        ObjectNode textContent = content.addObject();
        textContent.put("text", prompt);


        String payload = objectMapper.writeValueAsString(root);

        // 2. 发送 HTTP 请求
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
        conn.setDoOutput(true);
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(60000);

        try (var os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        // 3. 读取响应 (与之前相同)
        int responseCode = conn.getResponseCode();
        InputStream inputStream = (responseCode >= 200 && responseCode < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();
        String responseJson = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        if (responseCode != 200) {
            throw new RuntimeException("HTTP " + responseCode + ": " + responseJson);
        }

        // 4. 解析图片 URL (路径与之前相同)
        JsonNode rootNode = objectMapper.readTree(responseJson);
        JsonNode choices = rootNode.path("output").path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode contentArray = choices.get(0).path("message").path("content");
            if (contentArray.isArray() && contentArray.size() > 0) {
                String imageUrl = contentArray.get(0).path("image").asText();
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    return downloadImage(imageUrl);
                }
            }
        }
        throw new RuntimeException("未能提取图片 URL，响应：" + responseJson);
    }

    // downloadImage 方法保持不变
    private byte[] downloadImage(String imageUrl) throws Exception {
        URL url = new URL(imageUrl);
        try (InputStream in = url.openStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        }
    }
}