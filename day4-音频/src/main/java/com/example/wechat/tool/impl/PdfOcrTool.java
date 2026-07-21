package com.example.wechat.tool.impl;

import com.example.wechat.tool.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Slf4j
@Component
public class PdfOcrTool implements Tool {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===== 使用 SiliconFlow API =====
    @Value("${ocr.api.key:}")
    private String apiKey;

    @Value("${ocr.local.url:}")
    private String localOcrUrl;

    private static final String SILICONFLOW_OCR_URL = "https://api.siliconflow.cn/v1/chat/completions";

    public PdfOcrTool(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getName() {
        return "ocr_pdf";
    }

    @Override
    public String getDescription() {
        return "识别PDF文件内容，支持提取文字和表格";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> fileProp = new HashMap<>();
        fileProp.put("type", "string");
        fileProp.put("description", "PDF文件的Base64编码内容或URL");
        properties.put("file_content", fileProp);

        Map<String, Object> nameProp = new HashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "文件名");
        properties.put("file_name", nameProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{"file_content"});
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String fileContent = (String) params.get("file_content");
        String fileName = (String) params.get("file_name");

        if (fileContent == null || fileContent.trim().isEmpty()) {
            return "请提供PDF文件内容";
        }

        try {
            log.info("开始OCR识别PDF: {}", fileName != null ? fileName : "未知文件");

            // 将PDF转换为图片Base64
            String imageBase64;
            if (fileContent.startsWith("http://") || fileContent.startsWith("https://")) {
                imageBase64 = downloadAndConvertPdf(fileContent);
            } else {
                byte[] pdfBytes = Base64.getDecoder().decode(fileContent);
                imageBase64 = convertPdfToBase64(pdfBytes);
            }

            // 调用OCR API
            String result = callOcrApi(imageBase64);
            log.info("OCR识别完成，结果长度: {}", result.length());

            String header = fileName != null ? "📄 **" + fileName + "** 识别结果：\n\n" : "📄 **PDF识别结果**\n\n";
            return header + result;

        } catch (IllegalArgumentException e) {
            log.error("Base64解码失败", e);
            return "❌ PDF文件格式错误，请确认是有效的PDF文件";
        } catch (Exception e) {
            log.error("PDF识别失败", e);
            return "❌ PDF识别失败: " + e.getMessage();
        }
    }

    /**
     * 调用 OCR API（优先使用 SiliconFlow）
     */
    private String callOcrApi(String imageBase64) throws Exception {
        // 优先使用本地 OCR 服务
        if (localOcrUrl != null && !localOcrUrl.isEmpty()) {
            log.info("使用本地 OCR 服务: {}", localOcrUrl);
            return callLocalOcr(imageBase64);
        }

        // 使用 SiliconFlow API
        if (apiKey != null && !apiKey.isEmpty()) {
            log.info("使用 SiliconFlow API");
            return callSiliconFlowOcr(imageBase64);
        }

        return "❌ 未配置 OCR 服务，请配置 ocr.api.key 或 ocr.local.url";
    }

    /**
     * 调用 SiliconFlow OCR API
     */
    private String callSiliconFlowOcr(String imageBase64) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        // SiliconFlow 支持 deepseek-ocr 模型
        requestBody.put("model", "deepseek-ai/DeepSeek-OCR");

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");

        List<Map<String, Object>> content = new ArrayList<>();

        // 图片内容
        Map<String, Object> imageContent = new LinkedHashMap<>();
        imageContent.put("type", "image_url");
        imageContent.put("image_url", Map.of("url", "data:image/jpeg;base64," + imageBase64));
        content.add(imageContent);

        // 文本提示
        Map<String, Object> textContent = new LinkedHashMap<>();
        textContent.put("type", "text");
        textContent.put("text", "<image>\n<|grounding|>Convert the document to markdown with tables and formulas.");
        content.add(textContent);

        userMessage.put("content", content);
        messages.add(userMessage);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 4096);

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        log.debug("OCR请求体: {}", jsonBody);

        Request request = new Request.Builder()
                .url(SILICONFLOW_OCR_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            log.info("OCR响应状态码: {}", response.code());

            if (!response.isSuccessful()) {
                throw new Exception("OCR API调用失败: " + response.code() + ", " + responseBody);
            }

            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

            // 检查错误
            if (result.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) result.get("error");
                String message = (String) error.get("message");
                throw new Exception("OCR错误: " + message);
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                return (String) message.get("content");
            }
            return "未能解析OCR结果";
        }
    }

    /**
     * 调用本地 OCR 服务
     */
    private String callLocalOcr(String imageBase64) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("image", "data:image/jpeg;base64," + imageBase64);
        requestBody.put("prompt", "Convert the document to markdown with tables and formulas.");

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .url(localOcrUrl + "/ocr")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new Exception("本地OCR调用失败: " + response.code());
            }
            return responseBody;
        }
    }

    /**
     * 下载PDF并转换为图片
     */
    private String downloadAndConvertPdf(String pdfUrl) throws Exception {
        log.info("下载PDF: {}", pdfUrl);
        Request request = new Request.Builder().url(pdfUrl).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("下载PDF失败: " + response.code());
            }
            byte[] pdfBytes = response.body().bytes();
            return convertPdfToBase64(pdfBytes);
        }
    }

    /**
     * PDF转Base64图片（第一页）
     */
    private String convertPdfToBase64(byte[] pdfBytes) throws Exception {
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            if (document.getNumberOfPages() == 0) {
                throw new Exception("PDF没有页面");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImage(0, 1.5f);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            log.info("PDF转图片成功，大小: {} bytes", imageBytes.length);
            return Base64.getEncoder().encodeToString(imageBytes);
        }
    }
}