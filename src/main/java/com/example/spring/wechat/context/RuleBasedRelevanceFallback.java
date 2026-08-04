package com.example.spring.wechat.context;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class RuleBasedRelevanceFallback implements ConversationRelevanceClassifier {

    private static final List<String> STRONG_REFERENCE_MARKERS = List.of(
            "继续", "可以", "确认", "按刚才", "照刚才", "刚才那个", "刚刚那个",
            "上一个", "第二个", "这个", "那个", "改一下", "再优化", "就这样");

    @Override
    public ConversationRelevanceDecision classify(
            String userText,
            List<String> recentTurns,
            List<String> recentTopics) {
        String text = clean(userText);
        if (text.isBlank()) {
            return ConversationRelevanceDecision.weak("空消息默认弱相关");
        }
        if (containsAny(text, STRONG_REFERENCE_MARKERS)) {
            return ConversationRelevanceDecision.strong(
                    firstTopic(recentTopics),
                    "用户消息包含上下文指代表达，按强相关处理");
        }
        if (sharesTopicKeyword(text, recentTopics) || sharesTopicKeyword(text, recentTurns)) {
            return ConversationRelevanceDecision.strong(
                    firstTopic(recentTopics),
                    "用户消息与近期主题存在关键词重合，按强相关处理");
        }
        return ConversationRelevanceDecision.weak("未发现当前消息与近期主题的明显连续性");
    }

    private boolean sharesTopicKeyword(String text, List<String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String keyword : roughKeywords(text)) {
            if (keyword.length() < 2) {
                continue;
            }
            for (String value : values) {
                if (value != null && value.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> roughKeywords(String text) {
        return Arrays.stream(text.split("[\\s，。！？、,.!?；;：（）()【】\\[\\]\"']+"))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private boolean containsAny(String text, List<String> markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String firstTopic(List<String> topics) {
        return topics == null || topics.isEmpty() ? "" : clean(topics.get(0));
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
