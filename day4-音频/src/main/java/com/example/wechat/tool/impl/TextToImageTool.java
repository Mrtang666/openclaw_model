package com.example.wechat.tool.impl;

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.example.wechat.config.DashScopeProperties;
import com.example.wechat.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TextToImageTool implements Tool {

    private final ImageSynthesis imageSynthesis;
    private final DashScopeProperties dashScopeProperties;
    private final OkHttpClient httpClient;

    public TextToImageTool(ImageSynthesis imageSynthesis,
                           DashScopeProperties dashScopeProperties,
                           OkHttpClient httpClient) {
        this.imageSynthesis = imageSynthesis;
        this.dashScopeProperties = dashScopeProperties;
        this.httpClient = httpClient;
    }

    @Override
    public String getName() {
        return "text_to_image";
    }

    @Override
    public String getDescription() {
        return "根据文字描述生成图片（文生图）";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> promptProp = new HashMap<>();
        promptProp.put("type", "string");
        promptProp.put("description", "图片描述文字");
        properties.put("prompt", promptProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{"prompt"});
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String prompt = (String) params.get("prompt");

        log.info("========== 文生图工具被调用 ==========");
        log.info("提示词: {}", prompt);

        if (prompt == null || prompt.trim().isEmpty()) {
            return "请提供图片描述，例如：'一只可爱的小猫坐在草地上'";
        }

        try {
            // 1. 生成高清图片（使用1024x1024）
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashScopeProperties.getApiKey())
                    .model("wanx2.1-t2i-plus")
                    .prompt(prompt)
                    .n(1)
                    .size("1024*1024")  // 高清图片
                    .build();

            log.info("调用通义万相API...");
            ImageSynthesisResult result = imageSynthesis.call(param);

            if (result == null || result.getOutput() == null) {
                return "❌ 图片生成失败：API返回结果为空";
            }

            Object resultsObj = result.getOutput().getResults();
            if (resultsObj == null) {
                return "❌ 图片生成失败：未返回图片结果";
            }

            String imageUrl = extractUrlFromResults(resultsObj);
            log.info("提取到的URL: {}", imageUrl);

            if (imageUrl == null || imageUrl.isEmpty()) {
                return "❌ 图片生成失败：无法获取图片URL";
            }

            // 2. 下载图片（高清原图）
            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes == null) {
                return "❌ 图片下载失败，请重试";
            }
            log.info("图片大小: {} bytes", imageBytes.length);

            // 3. 转换为Base64，通过IMG:前缀返回
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            log.info("Base64大小: {} bytes", base64Image.length());

            // 4. 返回图片数据（不再压缩！）
            return "IMG:" + base64Image;

        } catch (ApiException | NoApiKeyException e) {
            log.error("文生图API调用失败", e);
            return "❌ 文生图失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("文生图失败", e);
            return "❌ 文生图失败: " + e.getMessage();
        }
    }

    /**
     * 下载图片
     */
    private byte[] downloadImage(String imageUrl) {
        try {
            Request request = new Request.Builder()
                    .url(imageUrl)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().bytes();
                }
            }
        } catch (Exception e) {
            log.error("下载图片异常", e);
        }
        return null;
    }

    /**
     * 从results中提取URL
     */
    private String extractUrlFromResults(Object resultsObj) {
        try {
            if (resultsObj instanceof List) {
                List<?> resultsList = (List<?>) resultsObj;
                if (resultsList.isEmpty()) {
                    return null;
                }

                Object firstResult = resultsList.get(0);

                if (firstResult instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) firstResult;
                    String[] possibleKeys = {"url", "Url", "URL", "image_url", "imageUrl"};
                    for (String key : possibleKeys) {
                        if (map.containsKey(key)) {
                            Object value = map.get(key);
                            if (value instanceof String) {
                                String url = (String) value;
                                if (url != null && !url.isEmpty()) {
                                    return url;
                                }
                            }
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("提取URL异常", e);
            return null;
        }
    }
}