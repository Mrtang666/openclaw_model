package com.youkeda.exercise.shared.picture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 通义千问 VL 视觉服务 - 使用 Jackson 解析响应
 */
@Service
public class QwenVisionService {

    private static final String API_KEY = "sk-ws-H.EDYDLLX.QWwS.MEYCIQCfGazv9lfSewDUwISsGzX9p1FJ1a9lgvSwmBaOmTSxZgIhAPNRqkix9kzct-4NpoHhmgxCwrwTi2EpImGGmHM1mSEH";
    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    // 创建 Jackson 解析器（线程安全，可复用）
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 分析图片内容（直接传入图片字节数组）
     */


    public String analyzeImage(byte[] imageData) {
        try {
            // 1. 转 Base64
            //String base64Image = Base64.getEncoder().encodeToString(imageData);
            String base64Image = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageData);
            // 2. 构建请求体（注意：image 字段只放纯 Base64，不需要 data:image 前缀）
            String payload = String.format(
                    "{\"model\":\"qwen-vl-plus\",\"input\":{\"messages\":[{\"role\":\"user\",\"content\":[{\"image\":\"%s\"},{\"text\":\"详细描述这张图片的内容，用中文回答\"}]}]}}",
                    base64Image
            );

            // 3. 发送请求
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            try (var os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            // 4. 读取响应
            int responseCode = conn.getResponseCode();
            InputStream inputStream = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            String responseJson = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            // 如果 HTTP 状态码不是 200，打印错误信息并返回
            if (responseCode != 200) {
                System.err.println("❌ Qwen API 报错，HTTP " + responseCode);
                System.err.println("响应内容：" + responseJson);
                return "❌ 图片分析服务异常（HTTP " + responseCode + "）";
            }

            // 5. ⭐ 使用 Jackson 解析嵌套 JSON
            return parseQwenResponse(responseJson);

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ 图片分析失败：" + e.getMessage();
        }
    }

    /**
     * 用 Jackson 安全解析 Qwen-VL 的返回结构
     * 结构：output.choices[0].message.content[0].text
     */
    private String parseQwenResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        // 检查是否有错误（某些版本的 Qwen 在顶层放 error）
        if (root.has("error")) {
            String errorMsg = root.path("error").path("message").asText("未知错误");
            return "❌ API 返回错误：" + errorMsg;
        }

        // 标准路径：output -> choices -> [0] -> message -> content -> [0] -> text
        JsonNode choices = root.path("output").path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isArray() && content.size() > 0) {
                JsonNode textNode = content.get(0).path("text");
                if (!textNode.isMissingNode() && !textNode.asText().isEmpty()) {
                    return textNode.asText();
                }
            }
        }

        // 兜底：尝试直接找 output.text（有些老版本是这个结构）
        JsonNode directText = root.path("output").path("text");
        if (!directText.isMissingNode() && !directText.asText().isEmpty()) {
            return directText.asText();
        }

        // 如果还是没有，打印完整 JSON 方便调试
        System.err.println("⚠️ 未能解析出 text 字段，完整响应：");
        System.err.println(json);
        return "⚠️ 未能解析图片内容，请稍后重试";
    }
}