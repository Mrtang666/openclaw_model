package com.example.spring.image.generation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ImageGenerationIntentParser {

    private static final List<String> KEYWORDS = List.of(
            "生成图片",
            "生成一张",
            "生成一幅",
            "生成一个",
            "画一张",
            "画一幅",
            "画个",
            "画一个",
            "画",
            "绘制",
            "做图",
            "做一张",
            "做一幅",
            "做一个",
            "做个",
            "海报",
            "插画",
            "头像",
            "表情包",
            "设计图",
            "配图",
            "生成配图",
            "生成海报",
            "出图",
            "出一张",
            "出一幅");

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
            "换一下",
            "再来一版",
            "重新生成",
            "再生成",
            "继续生成",
            "重新画",
            "重画",
            "重新出图",
            "你生成",
            "生成呀",
            "生成吧",
            "生成一下",
            "就这样生成",
            "按这个生成",
            "按这个来",
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
            "绘制",
            "做一张",
            "做一幅",
            "做个",
            "做",
            "画一张",
            "画一幅",
            "画一个",
            "出一张",
            "出一幅",
            "出",
            "一张",
            "一幅",
            "一个",
            "一组",
            "一套");

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
            "生成一张插画",
            "画一张",
            "画个",
            "画一幅",
            "画一个",
            "做图",
            "做一张",
            "做一幅",
            "做一个",
            "出图");

    public boolean matches(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        String normalized = normalize(input);
        if (looksLikeGenerateImageSentence(normalized)) {
            return true;
        }
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

        if (prompt.isBlank() || VAGUE_PROMPTS.contains(prompt) || prompt.length() < 2) {
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
        if (hasFollowUpKeyword || looksLikeNaturalRefinement(normalized)) {
            return Optional.of(input.strip());
        }
        return Optional.empty();
    }

    private String normalize(String value) {
        return value.strip().replaceAll("[\\s，。！？、：:,.!?]+", "");
    }

    private boolean looksLikeGenerateImageSentence(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }

        boolean hasGenerateVerb = normalized.contains("生成") || normalized.contains("画") || normalized.contains("做")
                || normalized.contains("出") || normalized.contains("绘制");
        if (!hasGenerateVerb) {
            return false;
        }

        return normalized.contains("图片")
                || normalized.contains("图像")
                || normalized.contains("海报")
                || normalized.contains("插画")
                || normalized.contains("头像")
                || normalized.contains("表情包")
                || normalized.contains("设计图");
    }

    private boolean looksLikeNaturalRefinement(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }

        boolean hasGentleModifier = normalized.contains("一点")
                || normalized.contains("一些")
                || normalized.contains("些");
        if (!hasGentleModifier) {
            return false;
        }

        return normalized.contains("更")
                || normalized.contains("再")
                || normalized.contains("换")
                || normalized.contains("调")
                || normalized.contains("改")
                || normalized.contains("高级")
                || normalized.contains("精神")
                || normalized.contains("明亮")
                || normalized.contains("专业")
                || normalized.contains("真实")
                || normalized.contains("干净")
                || normalized.contains("办公")
                || normalized.contains("场景")
                || normalized.contains("背景")
                || normalized.contains("风格")
                || normalized.contains("光线")
                || normalized.contains("色调")
                || normalized.contains("构图")
                || normalized.contains("人物")
                || normalized.contains("表情")
                || normalized.contains("动作");
    }
}
