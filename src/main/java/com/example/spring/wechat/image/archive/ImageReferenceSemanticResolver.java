package com.example.spring.wechat.image.archive;

import com.example.spring.chat.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于文本大模型的图片引用语义解析器。
 *
 * <p>规则解析器适合处理“第2张、这两张、全部图片”这类明确表达；
 * 这个解析器负责补充更自然的表达，例如“红色背景那张”“刚才那个对比图”“你生成的猫图”。
 * 大模型只返回结构化 JSON，最终是否真的选中图片仍由 Java 根据当前用户可用图片列表校验。</p>
 */
@Component
public class ImageReferenceSemanticResolver {

    private static final Logger log = LoggerFactory.getLogger(ImageReferenceSemanticResolver.class);

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ImageReferenceSemanticResolver(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /**
     * 调用大模型判断用户话语引用了哪些图片，并把模型返回结果校验成真实图片对象。
     */
    public ImageReferenceResolution resolve(String userText, List<ArchivedWechatImage> availableImages) {
        if (chatService == null || userText == null || userText.isBlank()
                || availableImages == null || availableImages.isEmpty()) {
            return ImageReferenceResolution.none();
        }

        try {
            String modelOutput = chatService.reply(buildPrompt(userText, availableImages));
            return parse(modelOutput, availableImages);
        } catch (RuntimeException exception) {
            log.warn("图片引用语义解析失败，将回退到规则兜底，text={}, error={}",
                    preview(userText), rootMessage(exception));
            return ImageReferenceResolution.none();
        }
    }

    private ImageReferenceResolution parse(String modelOutput, List<ArchivedWechatImage> availableImages) {
        String json = extractJsonObject(modelOutput);
        if (json.isBlank()) {
            return ImageReferenceResolution.none();
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            boolean needsClarification = root.path("needs_clarification").asBoolean(false);
            String question = root.path("clarification_question").asText("");
            if (needsClarification) {
                return ImageReferenceResolution.clarification(question.isBlank()
                        ? "你想让我使用哪一张或哪几张图片？"
                        : question);
            }

            Map<Integer, ArchivedWechatImage> byIndex = new LinkedHashMap<>();
            for (ArchivedWechatImage image : availableImages) {
                if (image != null) {
                    byIndex.put(image.imageIndex(), image);
                }
            }

            List<ArchivedWechatImage> selected = new ArrayList<>();
            JsonNode indexes = root.path("selected_image_indexes");
            if (indexes.isArray()) {
                for (JsonNode indexNode : indexes) {
                    int index = indexNode.asInt(-1);
                    ArchivedWechatImage image = byIndex.get(index);
                    if (image != null && !selected.contains(image)) {
                        selected.add(image);
                    }
                }
            }
            return selected.isEmpty() ? ImageReferenceResolution.none() : ImageReferenceResolution.selected(selected);
        } catch (Exception exception) {
            log.warn("图片引用语义解析 JSON 失败，将回退到规则兜底，output={}, error={}",
                    preview(modelOutput), rootMessage(exception));
            return ImageReferenceResolution.none();
        }
    }

    private String buildPrompt(String userText, List<ArchivedWechatImage> availableImages) {
        return """
                你是图片引用语义解析器，只负责判断用户当前这句话引用了哪些已存在图片。
                请只输出一个 JSON 对象，不要输出 Markdown，不要解释。

                输出格式：
                {
                  "selected_image_indexes": [图片编号],
                  "needs_clarification": false,
                  "clarification_question": "",
                  "reason": "一句话说明为什么这么选"
                }

                判断规则：
                1. 只能从“当前可用图片资源”中选择，不允许编造编号。
                2. 如果用户明确说“全部、所有、all”，可以选择所有相关图片；否则不要默认选择全部历史图片。
                3. “这张、这两张、刚刚发的、最近发的”通常指最近一次用户上传的图片批次。
                4. “你生成的、AI 生成的、刚才生成的”通常指最近的 AI 生成图片。
                5. 如果用户按图片内容描述，例如“红色背景那张、猫那张、截图那张”，请结合文件名、描述、生成提示词判断。
                6. 如果无法确定，输出 needs_clarification=true，并给出简短追问。

                当前可用图片资源：
                %s

                用户当前需求：
                %s
                """.formatted(imageContext(availableImages), userText.strip());
    }

    private String imageContext(List<ArchivedWechatImage> images) {
        StringBuilder builder = new StringBuilder();
        for (ArchivedWechatImage image : images) {
            if (image == null) {
                continue;
            }
            builder.append("- 编号：").append(image.imageIndex())
                    .append("；来源：").append(sourceLabel(image.sourceType()))
                    .append("；状态：").append(image.status())
                    .append("；消息ID：").append(image.messageId())
                    .append("；文件名：").append(image.fileName());
            appendIfPresent(builder, "；描述：", image.description());
            appendIfPresent(builder, "；生成提示词：", image.prompt());
            appendIfPresent(builder, "；来源引用：", image.sourceReference());
            builder.append('\n');
        }
        return builder.toString().strip();
    }

    private void appendIfPresent(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(label).append(value.strip());
        }
    }

    private String sourceLabel(ImageArchiveSourceType sourceType) {
        if (sourceType == ImageArchiveSourceType.AI_GENERATED) {
            return "AI生成";
        }
        return "用户上传";
    }

    private String extractJsonObject(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return text.substring(start, end + 1);
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replaceAll("\\s+", " ").strip();
        return text.length() <= 120 ? text : text.substring(0, 120) + "...";
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return Optional.ofNullable(current.getMessage()).orElse(current.getClass().getSimpleName());
    }
}
