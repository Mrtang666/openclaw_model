package com.example.wechat.tool.impl;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.example.wechat.config.DashScopeProperties;
import com.example.wechat.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ImageToTextTool implements Tool {

    private final DashScopeProperties dashScopeProperties;

    public ImageToTextTool(DashScopeProperties dashScopeProperties) {
        this.dashScopeProperties = dashScopeProperties;
    }

    @Override
    public String getName() {
        return "image_to_text";
    }

    @Override
    public String getDescription() {
        return "理解图片内容，将图片转换为文字描述（图生文）";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> imageProp = new HashMap<>();
        imageProp.put("type", "string");
        imageProp.put("description", "图片的Base64编码内容");
        properties.put("image_content", imageProp);

        Map<String, Object> promptProp = new HashMap<>();
        promptProp.put("type", "string");
        promptProp.put("description", "对图片的理解要求");
        properties.put("prompt", promptProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{"image_content"});
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String imageContent = (String) params.get("image_content");
        String prompt = (String) params.getOrDefault("prompt", "请详细描述这张图片的内容");

        log.info("========== ImageToTextTool.execute 被调用 ==========");
        log.info("图片Base64长度: {}", imageContent != null ? imageContent.length() : 0);
        log.info("提示词: {}", prompt);

        if (imageContent == null || imageContent.trim().isEmpty()) {
            return "请提供图片内容";
        }

        try {
            log.info("开始调用通义千问VL API...");
            String result = callMultiModalConversation(imageContent, prompt);
            log.info("图生文结果: {}", result);
            return "📷 图片识别结果:\n" + result;
        } catch (Exception e) {
            log.error("图生文失败", e);
            return "图生文失败: " + e.getMessage();
        }
    }

    private String callMultiModalConversation(String imageBase64, String prompt)
            throws Exception {

        String imageDataUri = "data:image/jpeg;base64," + imageBase64;

        List<Map<String, Object>> contentList = new ArrayList<>();

        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("image", imageDataUri);
        contentList.add(imageContent);

        Map<String, Object> textContent = new HashMap<>();
        textContent.put("text", prompt);
        contentList.add(textContent);

        MultiModalMessage userMessage = MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(contentList)
                .build();

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(dashScopeProperties.getApiKey())
                .model("qwen-vl-plus")
                .messages(Collections.singletonList(userMessage))
                .build();

        MultiModalConversation conversation = new MultiModalConversation();
        MultiModalConversationResult result = conversation.call(param);

        return extractResponseText(result);
    }

    private String extractResponseText(MultiModalConversationResult result) {
        try {
            List<Map<String, Object>> contentList = result.getOutput()
                    .getChoices().get(0)
                    .getMessage().getContent();

            if (contentList == null || contentList.isEmpty()) {
                return "响应内容为空";
            }

            // 遍历contentList，提取文本内容
            for (Map<String, Object> item : contentList) {
                log.info("content item keys: {}", item.keySet());

                // 方式1：直接检查是否有 "text" 这个key（根据日志，返回的格式是 {text=...}）
                if (item.containsKey("text")) {
                    String text = (String) item.get("text");
                    if (text != null && !text.isEmpty()) {
                        return text;
                    }
                }

                // 方式2：检查是否有 "type" 字段且值为 "text"
                if ("text".equals(item.get("type"))) {
                    String text = (String) item.get("text");
                    if (text != null && !text.isEmpty()) {
                        return text;
                    }
                }
            }

            // 如果上面都没找到，尝试直接从contentList中获取第一个元素的text
            Map<String, Object> firstItem = contentList.get(0);
            if (firstItem.containsKey("text")) {
                return (String) firstItem.get("text");
            }

            return "未能提取图片描述内容";

        } catch (Exception e) {
            log.error("解析响应失败", e);
            return "解析响应失败: " + e.getMessage();
        }
    }
}