package com.example.spring.wechat.bot;

import com.example.spring.wechat.image.generation.model.ImageGenerationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 微信会话服务返回给发送层的统一回复对象。
 * text 表示普通文本，image 表示待发送图片，preImageTexts 表示图片前的说明文字，
 * parts 用于保存需要按顺序发送的文本、图片、语音等多段回复。
 */
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

    public record Part(String text, ImageGenerationResult image, Voice voice, FileAttachment file) {

        public Part(String text, ImageGenerationResult image, Voice voice) {
            this(text, image, voice, null);
        }

        public static Part text(String text) {
            return new Part(text, null, null, null);
        }

        public static Part image(String caption, ImageGenerationResult image) {
            return new Part(caption, image, null, null);
        }

        public static Part voice(Voice voice) {
            return new Part(null, null, voice, null);
        }

        public static Part textAndVoice(String text, Voice voice) {
            return new Part(text, null, voice, null);
        }

        public static Part file(FileAttachment file) {
            return new Part(file == null ? null : file.caption(), null, null, file);
        }

        public boolean hasImage() {
            return image != null && image.imageBytes() != null && image.imageBytes().length > 0;
        }

        public boolean hasVoice() {
            return voice != null && voice.audioBytes() != null && voice.audioBytes().length > 0;
        }

        public boolean hasFile() {
            return file != null && file.fileBytes() != null && file.fileBytes().length > 0;
        }

        private boolean hasContent() {
            return hasImage() || hasVoice() || hasFile() || (text != null && !text.isBlank());
        }
    }

    public record FileAttachment(
            byte[] fileBytes,
            String fileName,
            String contentType,
            String caption) {

        public FileAttachment {
            if (fileBytes != null) {
                fileBytes = fileBytes.clone();
            }
            fileName = fileName == null || fileName.isBlank() ? "document.bin" : fileName.strip();
            contentType = contentType == null ? "application/octet-stream" : contentType.strip();
            caption = caption == null ? "" : caption.strip();
        }

        @Override
        public byte[] fileBytes() {
            return fileBytes == null ? null : fileBytes.clone();
        }
    }

    public record Voice(
            byte[] audioBytes,
            String fileName,
            Integer durationMs,
            Integer sampleRate,
            Integer encodeType,
            Integer bitsPerSample,
            String transcriptText) {

        public Voice {
            if (audioBytes != null) {
                audioBytes = audioBytes.clone();
            }
            fileName = fileName == null || fileName.isBlank() ? "reply.silk" : fileName.strip();
            durationMs = durationMs == null || durationMs <= 0 ? 1000 : durationMs;
            sampleRate = sampleRate == null || sampleRate <= 0 ? 16000 : sampleRate;
            encodeType = encodeType == null ? 6 : encodeType;
            bitsPerSample = bitsPerSample == null ? 16 : bitsPerSample;
            transcriptText = transcriptText == null ? "" : transcriptText.strip();
        }
    }
}

