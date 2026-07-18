package com.example.spring.wechat.bot;

import com.example.spring.image.generation.ImageGenerationResult;

import java.util.ArrayList;
import java.util.List;

public record WechatReply(String text, ImageGenerationResult image, List<String> preImageTexts, List<Part> parts) {

    public WechatReply {
        preImageTexts = preImageTexts == null ? List.of() : List.copyOf(preImageTexts);
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    public WechatReply(String text, ImageGenerationResult image) {
        this(text, image, List.of(), List.of());
    }

    public static WechatReply text(String text) {
        return new WechatReply(text, null, List.of(), List.of());
    }

    public static WechatReply textAndImage(String text, ImageGenerationResult image) {
        return new WechatReply(text, image, List.of(), List.of());
    }

    public static WechatReply textsAndImage(List<String> preImageTexts, String text, ImageGenerationResult image) {
        List<Part> parts = new ArrayList<>();
        if (preImageTexts != null) {
            preImageTexts.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(Part::text)
                    .forEach(parts::add);
        }
        parts.add(Part.image(text, image));
        return new WechatReply(text, image, preImageTexts, parts);
    }

    public static WechatReply ordered(List<Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return text("");
        }

        List<Part> safeParts = parts.stream()
                .filter(Part::hasContent)
                .toList();
        if (safeParts.isEmpty()) {
            return text("");
        }

        Part last = safeParts.get(safeParts.size() - 1);
        ImageGenerationResult firstImage = safeParts.stream()
                .filter(Part::hasImage)
                .map(Part::image)
                .findFirst()
                .orElse(null);
        return new WechatReply(last.text(), firstImage, List.of(), safeParts);
    }

    public boolean hasImage() {
        return image != null && image.imageBytes() != null && image.imageBytes().length > 0;
    }

    public record Part(String text, ImageGenerationResult image) {

        public static Part text(String text) {
            return new Part(text, null);
        }

        public static Part image(String caption, ImageGenerationResult image) {
            return new Part(caption, image);
        }

        public boolean hasImage() {
            return image != null && image.imageBytes() != null && image.imageBytes().length > 0;
        }

        private boolean hasContent() {
            return hasImage() || (text != null && !text.isBlank());
        }
    }
}
