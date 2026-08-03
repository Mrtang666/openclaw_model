package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Primary
@Component
public class ModelConversationRelevanceClassifier implements ConversationRelevanceClassifier {

    private static final Logger log = LoggerFactory.getLogger(ModelConversationRelevanceClassifier.class);

    private final ChatService chatService;
    private final RuleBasedRelevanceFallback fallback;
    private final WechatContextProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public ModelConversationRelevanceClassifier(
            ChatService chatService,
            RuleBasedRelevanceFallback fallback,
            WechatContextProperties properties) {
        this(chatService, fallback, properties, new ObjectMapper());
    }

    ModelConversationRelevanceClassifier(
            ChatService chatService,
            RuleBasedRelevanceFallback fallback,
            WechatContextProperties properties,
            ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.fallback = fallback == null ? new RuleBasedRelevanceFallback() : fallback;
        this.properties = properties;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public ConversationRelevanceDecision classify(
            String userText,
            List<String> recentTurns,
            List<String> recentTopics) {
        if (properties == null || !properties.relevanceClassifierEnabled() || chatService == null) {
            return fallback.classify(userText, recentTurns, recentTopics);
        }
        try {
            String reply = chatService.reply(prompt(userText, recentTurns, recentTopics));
            return parse(reply);
        } catch (RuntimeException exception) {
            log.warn("上下文相关性模型判断失败，error={}", rootMessage(exception));
            return fallback.classify(userText, recentTurns, recentTopics);
        }
    }

    private String prompt(String userText, List<String> recentTurns, List<String> recentTopics) {
        return """
                你是微信 Agent 的上下文主题相关性分类器。
                判断“当前用户消息”和“近期上下文主题”是否属于同一个主题。
                只输出 JSON，不要输出解释文字。

                JSON 字段：
                - relevance: STRONG 或 WEAK
                - confidence: 0 到 1
                - currentTopic: 当前主题，不能确定则为空字符串
                - reason: 一句话理由
                - relatedTopics: 字符串数组

                判定标准：
                - STRONG：当前消息明显延续近期主题，或通过“继续、刚才、这个、第二个”等表达依赖近期上下文。
                - WEAK：新话题、不相关、主题跨度很大，或无法可靠判断为同一主题。

                当前用户消息：
                %s

                最近对话：
                %s

                最近主题：
                %s
                """.formatted(clean(userText), join(recentTurns), join(recentTopics));
    }

    private ConversationRelevanceDecision parse(String reply) {
        try {
            JsonNode root = objectMapper.readTree(cleanJson(reply));
            RelevanceLevel level = "STRONG".equalsIgnoreCase(root.path("relevance").asText(""))
                    ? RelevanceLevel.STRONG
                    : RelevanceLevel.WEAK;
            List<String> topics = new ArrayList<>();
            JsonNode related = root.path("relatedTopics");
            if (related.isArray()) {
                for (JsonNode item : related) {
                    if (item != null && item.isTextual() && !item.asText().isBlank()) {
                        topics.add(item.asText().strip());
                    }
                }
            }
            return new ConversationRelevanceDecision(
                    level,
                    root.path("confidence").asDouble(0),
                    root.path("currentTopic").asText(""),
                    root.path("reason").asText(""),
                    topics);
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid relevance classifier JSON", exception);
        }
    }

    private String cleanJson(String value) {
        String text = clean(value);
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").strip();
        }
        return text;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "无";
        }
        return String.join(System.lineSeparator(), values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .limit(8)
                .toList());
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
