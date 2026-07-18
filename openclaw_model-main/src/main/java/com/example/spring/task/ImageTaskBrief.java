package com.example.spring.task;

import java.util.ArrayList;
import java.util.List;

public record ImageTaskBrief(
    String subject,
    String purpose,
    String style,
    String scene,
    String composition,
    String colors,
    String aspectRatio,
    String visibleText,
    String negativePrompt,
    String sourceMode) {

    public ImageTaskBrief {
        subject = normalize(subject);
        purpose = normalize(purpose);
        style = normalize(style);
        scene = normalize(scene);
        composition = normalize(composition);
        colors = normalize(colors);
        aspectRatio = normalize(aspectRatio);
        visibleText = normalize(visibleText);
        negativePrompt = normalize(negativePrompt);
        sourceMode = normalize(sourceMode);
    }

    public static ImageTaskBrief empty() {
        return new ImageTaskBrief("", "", "", "", "", "", "", "", "", "");
    }

    public ImageTaskBrief merge(ImageTaskBrief update) {
        if (update == null) {
            return this;
        }
        return new ImageTaskBrief(
            prefer(update.subject, subject),
            prefer(update.purpose, purpose),
            prefer(update.style, style),
            prefer(update.scene, scene),
            prefer(update.composition, composition),
            prefer(update.colors, colors),
            prefer(update.aspectRatio, aspectRatio),
            prefer(update.visibleText, visibleText),
            prefer(update.negativePrompt, negativePrompt),
            prefer(update.sourceMode, sourceMode));
    }

    public String toGenerationPrompt(String fallbackText) {
        List<String> parts = new ArrayList<>();
        add(parts, "任务主体", subject);
        add(parts, "使用场景", purpose);
        add(parts, "画面风格", style);
        add(parts, "背景环境", scene);
        add(parts, "构图要求", composition);
        add(parts, "色彩要求", colors);
        add(parts, "画面比例", aspectRatio);
        add(parts, "画面文字", visibleText);
        add(parts, "不要出现", negativePrompt);
        if (parts.isEmpty() && fallbackText != null && !fallbackText.isBlank()) {
            return fallbackText.trim();
        }
        return "请根据以下已确认需求生成或编辑图片：\n" + String.join("\n", parts);
    }

    private static void add(List<String> parts, String label, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(label + "：" + value);
        }
    }

    private static String prefer(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
