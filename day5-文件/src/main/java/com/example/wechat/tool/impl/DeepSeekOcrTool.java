package com.example.wechat.tool.impl;

import com.example.wechat.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DeepSeekOcrTool implements Tool {

    private final OkHttpClient httpClient;

    @Value("${deepseek.ocr.api-key:}")
    private String apiKey;

    @Value("${deepseek.ocr.base-url:https://api.siliconflow.cn/v1/chat/completions}")
    private String baseUrl;

    @Value("${deepseek.ocr.model:deepseek-ai/DeepSeek-OCR}")
    private String modelName;

    public DeepSeekOcrTool() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() {
        return "ocr_document";
    }

    @Override
    public String getDescription() {
        return "识别图片或PDF中的文字内容，支持表格、标题等文档结构提取";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> fileProp = new HashMap<>();
        fileProp.put("type", "string");
        fileProp.put("description", "文件的Base64编码内容");
        properties.put("file_content", fileProp);

        Map<String, Object> modeProp = new HashMap<>();
        modeProp.put("type", "string");
        modeProp.put("description", "识别模式: free_ocr(快速), grounding(复杂表格), ocr_image(逐字提取)");
        modeProp.put("default", "free_ocr");
        properties.put("mode", modeProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{"file_content"});
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String fileContent = (String) params.get("file_content");
        String mode = (String) params.getOrDefault("mode", "free_ocr");

        if (fileContent == null || fileContent.trim().isEmpty()) {
            return "请提供要识别的文件内容";
        }

        if (apiKey == null || apiKey.isEmpty()) {
            return "❌ OCR 服务未配置 API Key，请在 application.yml 中设置 deepseek.ocr.api-key";
        }

        try {
            log.info("📄 开始 OCR 识别，模式: {}", mode);

            // 构建提示词 - 根据模式选择
            String prompt = buildPrompt(mode);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");

            // 多模态内容
            java.util.List<Map<String, Object>> contentList = new java.util.ArrayList<>();

            // 图片内容 (Base64)
            Map<String, Object> imageContent = new HashMap<>();
            // 判断是否为 PDF（根据Base64头判断）
            if (fileContent.startsWith("JVBER")) {
                // PDF 通过 data URI 传递
                imageContent.put("image", "data:application/pdf;base64," + fileContent);
            } else {
                imageContent.put("image", "data:image/jpeg;base64," + fileContent);
            }
            contentList.add(imageContent);

            // 文本提示
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("text", prompt);
            contentList.add(textContent);

            message.put("content", contentList);
            requestBody.put("messages", new Object[]{message});

            // 参数设置
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("max_tokens", 4096);
            parameters.put("temperature", 0.1);
            requestBody.put("parameters", parameters);

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonBody = mapper.writeValueAsString(requestBody);

            log.debug("OCR 请求体: {}", jsonBody);

            // 发送请求
            Request request = new Request.Builder()
                    .url(baseUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                log.info("OCR 响应状态码: {}", response.code());

                if (!response.isSuccessful()) {
                    return "❌ OCR 服务调用失败: " + response.code() + "\n" + responseBody;
                }

                // 解析响应
                Map<String, Object> result = mapper.readValue(responseBody, Map.class);

                if (result.containsKey("error")) {
                    Map<String, Object> error = (Map<String, Object>) result.get("error");
                    return "❌ OCR 错误: " + error.get("message");
                }

                // 提取文本内容
                String extractedText = extractTextFromResponse(result);
                if (extractedText == null || extractedText.isEmpty()) {
                    return "⚠️ OCR 识别完成，但未提取到文本内容";
                }

                // 限制输出长度（微信消息限制）
                if (extractedText.length() > 3500) {
                    extractedText = extractedText.substring(0, 3500) + "\n\n...(内容过长已截断)";
                }

                return "📄 **OCR 识别结果**\n\n" + extractedText;

            }

        } catch (IOException e) {
            log.error("OCR 请求异常", e);
            return "❌ OCR 请求异常: " + e.getMessage();
        } catch (Exception e) {
            log.error("OCR 处理异常", e);
            return "❌ OCR 处理异常: " + e.getMessage();
        }
    }

    /**
     * 根据模式构建提示词
     */
    private String buildPrompt(String mode) {
        switch (mode) {
            case "grounding":
                return "<image>\n<|grounding|>Convert the document to markdown with accurate table structure.";
            case "ocr_image":
                return "<image>\nExtract all text from this image with precise word-level detail.";
            case "free_ocr":
            default:
                return "<image>\nConvert the document to markdown.";
        }
    }

    /**
     * 从响应中提取文本
     */
    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(Map<String, Object> result) {
        try {
            // OpenAI 兼容格式
            if (result.containsKey("choices")) {
                java.util.List<Map<String, Object>> choices =
                        (java.util.List<Map<String, Object>>) result.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    if (message != null) {
                        return (String) message.get("content");
                    }
                }
            }

            // 直接返回格式
            if (result.containsKey("text")) {
                return (String) result.get("text");
            }

            // 其他格式兜底
            return result.toString();

        } catch (Exception e) {
            log.error("解析响应失败", e);
            return null;
        }
    }
}