package com.example.spring.image.generation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ImageGenerationIntentParser {

    private static final List<String> KEYWORDS = List.of(
            "生成图片",
            "画一张",
            "画一幅",
            "画个",
            "画",
            "做图",
            "海报",
            "插画",
            "头像",
            "表情包",
            "设计图",
            "出图",
            "绘制");

    private static final List<String> FOLLOW_UP_KEYWORDS = List.of(
            "不要",
            "去掉",
            "取消",
            "删除",
            "改成",
            "改为",
            "换成",
            "替换",
            "调整",
            "修改",
            "优化",
            "增加",
            "添加",
            "加上",
            "减少",
            "变成",
            "重新生成",
            "再生成",
            "继续生成",
            "重新画",
            "重画",
            "重新出图",
            "你生成",
            "生成呀",
            "生成吧",
            "就这样生成",
            "按这个生成",
            "出图");

    private static final List<String> CONFIRMATION_PROMPTS = List.of(
            "你生成呀",
            "你生成",
            "生成呀",
            "生成吧",
            "生成",
            "就这样生成",
            "按这个生成",
            "继续生成",
            "重新生成",
            "再生成",
            "出图",
            "重新出图");

    private static final List<String> LEADING_PREFIXES = List.of(
            "帮我",
            "请帮我",
            "请",
            "麻烦",
            "我想要",
            "我想",
            "想要",
            "给我",
            "帮忙",
            "生成",
            "画",
            "做一张",
            "做个",
            "做",
            "画一张",
            "画一幅",
            "出一张",
            "出");

    private static final List<String> VAGUE_PROMPTS = List.of(
            "图片",
            "图",
            "一张图",
            "一张图片",
            "照片",
            "海报",
            "插画",
            "头像",
            "表情包",
            "设计图",
            "生成图片",
            "生成一张图片",
            "生成一张图",
            "画一张",
            "画个",
            "画一幅",
            "做图",
            "出图");

    public boolean matches(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        String normalized = normalize(input);
        return KEYWORDS.stream().anyMatch(normalized::contains);
    }

    public Optional<String> extractPrompt(String input) {
        if (!matches(input)) {
            return Optional.empty();
        }

        String prompt = normalize(input);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : LEADING_PREFIXES) {
                if (prompt.startsWith(prefix)) {
                    prompt = prompt.substring(prefix.length());
                    changed = true;
                    break;
                }
            }
        }
        prompt = prompt.strip();

        if (prompt.isBlank() || VAGUE_PROMPTS.contains(prompt)) {
            return Optional.empty();
        }

        return Optional.of(prompt);
    }

    public Optional<String> extractFollowUpInstruction(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(input);
        if (CONFIRMATION_PROMPTS.contains(normalized)) {
            return Optional.of("按上一轮图片需求重新生成");
        }

        boolean hasFollowUpKeyword = FOLLOW_UP_KEYWORDS.stream().anyMatch(normalized::contains);
        if (!hasFollowUpKeyword) {
            return Optional.empty();
        }

        return Optional.of(input.strip());
    }

    private String normalize(String value) {
        return value.strip().replaceAll("[\\s，。！？、：:,.!?]+", "");
    }
}
