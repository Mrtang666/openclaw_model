package com.example.spring.wechat.image.archive;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信图片资源引用解析器。
 *
 * <p>这个类只负责判断“用户当前这句话应该使用哪些图片”，不调用模型，也不执行具体工具。
 * 为了避免边界过宽，只有用户明确说“全部、所有、all”时才会选择历史全部图片；
 * “这些图、这两张、对比图片”等表达默认只指向当前待处理图片或最近一次上传的图片批次。</p>
 */
@Component
public class ImageReferenceResolver {

    private static final Pattern EXPLICIT_INDEX_PATTERN =
            Pattern.compile("(?:第\\s*)?(\\d{1,2})\\s*(?:张|个|幅|image|photo|picture)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNT_PATTERN =
            Pattern.compile("(?:这|最近|latest|these)?\\s*(\\d{1,2})\\s*(?:张|个|幅|images|photos|pictures)", Pattern.CASE_INSENSITIVE);

    /**
     * 根据用户文字和当前可用图片资源，解析出本轮真正要使用的图片范围。
     */
    public ImageReferenceResolution resolve(String userText, List<ArchivedWechatImage> images) {
        List<ArchivedWechatImage> available = safeImages(images);
        if (available.isEmpty()) {
            return ImageReferenceResolution.none();
        }

        String text = normalize(userText);
        List<Integer> requestedIndexes = requestedIndexes(text);
        if (!requestedIndexes.isEmpty()) {
            List<ArchivedWechatImage> selected = available.stream()
                    .filter(image -> requestedIndexes.contains(image.imageIndex()))
                    .toList();
            return selected.isEmpty() ? ImageReferenceResolution.none() : ImageReferenceResolution.selected(selected);
        }

        if (mentionsAllImages(text)) {
            return ImageReferenceResolution.selected(available);
        }

        if (mentionsAssistantGeneratedImage(text)) {
            return latestOf(available, ImageArchiveSourceType.AI_GENERATED)
                    .map(image -> ImageReferenceResolution.selected(List.of(image)))
                    .orElseGet(ImageReferenceResolution::none);
        }

        if (mentionsUserUploadedImage(text)) {
            return latestOf(available, ImageArchiveSourceType.USER_UPLOAD)
                    .map(image -> ImageReferenceResolution.selected(List.of(image)))
                    .orElseGet(ImageReferenceResolution::none);
        }

        if (mentionsSingularImage(text) && hasUserAndGeneratedImages(available)) {
            return ImageReferenceResolution.clarification(
                    "我找到了用户上传图片和 AI 生成图片，你想使用哪一张？");
        }

        int requestedCount = requestedImageCount(text);
        if (requestedCount > 0) {
            List<ArchivedWechatImage> latest = latestUserUploads(available, requestedCount);
            return latest.isEmpty() ? ImageReferenceResolution.none() : ImageReferenceResolution.selected(latest);
        }

        if (mentionsCurrentImageGroup(text)) {
            List<ArchivedWechatImage> pending = pendingImages(available);
            if (!pending.isEmpty()) {
                return ImageReferenceResolution.selected(pending);
            }
            List<ArchivedWechatImage> latestBatch = latestUserUploadBatch(available);
            return latestBatch.isEmpty() ? ImageReferenceResolution.none() : ImageReferenceResolution.selected(latestBatch);
        }

        List<ArchivedWechatImage> pending = pendingImages(available);
        if (!pending.isEmpty()) {
            return ImageReferenceResolution.selected(pending);
        }

        if (mentionsSingularImage(text)) {
            return available.stream()
                    .max(Comparator.comparing(ArchivedWechatImage::createdAt))
                    .map(image -> ImageReferenceResolution.selected(List.of(image)))
                    .orElseGet(ImageReferenceResolution::none);
        }

        return ImageReferenceResolution.none();
    }

    /**
     * 当用户明显在说图片，但自然语言没有精确指向时，返回一个安全的默认图片范围。
     *
     * <p>这个默认范围不能返回全部历史图片，否则“对比这两张图片生成 PDF”这类需求会把旧图片也放进文档。
     * 因此这里依次选择：当前待处理图片、最近一批用户上传图片、最近一张图片。</p>
     */
    public List<ArchivedWechatImage> defaultReferenceScope(List<ArchivedWechatImage> images) {
        List<ArchivedWechatImage> available = safeImages(images);
        List<ArchivedWechatImage> pending = pendingImages(available);
        if (!pending.isEmpty()) {
            return pending;
        }
        List<ArchivedWechatImage> latestBatch = latestUserUploadBatch(available);
        if (!latestBatch.isEmpty()) {
            return latestBatch;
        }
        return available.stream()
                .max(Comparator.comparing(ArchivedWechatImage::createdAt))
                .map(List::of)
                .orElse(List.of());
    }

    private List<ArchivedWechatImage> safeImages(List<ArchivedWechatImage> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.stream()
                .filter(image -> image != null)
                .sorted(Comparator.comparing(ArchivedWechatImage::createdAt)
                        .thenComparing(ArchivedWechatImage::imageIndex))
                .toList();
    }

    private String normalize(String userText) {
        return userText == null ? "" : userText.strip().toLowerCase(Locale.ROOT);
    }

    private List<Integer> requestedIndexes(String text) {
        java.util.ArrayList<Integer> indexes = new java.util.ArrayList<>();
        Matcher matcher = EXPLICIT_INDEX_PATTERN.matcher(text);
        while (matcher.find()) {
            if (!looksLikeCountReference(text, matcher.start())) {
                addNumber(indexes, matcher.group(1));
            }
        }
        addOrdinal(indexes, text, 1, "first", "1st", "第一", "第一张");
        addOrdinal(indexes, text, 2, "second", "2nd", "第二", "第二张");
        addOrdinal(indexes, text, 3, "third", "3rd", "第三", "第三张");
        addOrdinal(indexes, text, 4, "fourth", "4th", "第四", "第四张");
        addOrdinal(indexes, text, 5, "fifth", "5th", "第五", "第五张");
        return indexes.stream().distinct().toList();
    }

    private boolean looksLikeCountReference(String text, int matchStart) {
        int start = Math.max(0, matchStart - 4);
        String prefix = text.substring(start, matchStart);
        return prefix.contains("这") || prefix.contains("最近") || prefix.contains("these") || prefix.contains("latest");
    }

    private int requestedImageCount(String text) {
        Matcher matcher = COUNT_PATTERN.matcher(text);
        if (matcher.find() && looksLikeCountReference(text, matcher.start())) {
            try {
                return Math.max(0, Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        if (containsAny(text, "two images", "two photos", "two pictures", "这两张", "这两个", "两张图", "两张图片", "两个图片")) {
            return 2;
        }
        if (containsAny(text, "three images", "three photos", "three pictures", "这三张", "三张图", "三张图片")) {
            return 3;
        }
        if (containsAny(text, "five images", "five photos", "five pictures", "这五张", "五张图", "五张图片")) {
            return 5;
        }
        return 0;
    }

    private void addNumber(List<Integer> indexes, String value) {
        try {
            int index = Integer.parseInt(value);
            if (index > 0) {
                indexes.add(index);
            }
        } catch (NumberFormatException ignored) {
            // 忽略无法解析的数字片段。
        }
    }

    private void addOrdinal(List<Integer> indexes, String text, int index, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase.toLowerCase(Locale.ROOT))) {
                indexes.add(index);
                return;
            }
        }
    }

    private boolean mentionsAllImages(String text) {
        return containsAny(text, "all images", "all photos", "all pictures", "全部图片", "所有图片", "全部图", "所有图");
    }

    private boolean mentionsCurrentImageGroup(String text) {
        return containsAny(text,
                "these images",
                "these photos",
                "these pictures",
                "current images",
                "latest images",
                "compare images",
                "compare photos",
                "这些图",
                "这些图片",
                "这几张",
                "这几张图",
                "这批图",
                "这组图",
                "对比图片",
                "对比照片");
    }

    private boolean mentionsAssistantGeneratedImage(String text) {
        return containsAny(text,
                "you generated",
                "you just generated",
                "ai-generated",
                "ai generated",
                "generated image",
                "你生成",
                "你刚生成",
                "ai生成",
                "系统生成");
    }

    private boolean mentionsUserUploadedImage(String text) {
        return containsAny(text,
                "i sent",
                "i uploaded",
                "uploaded image",
                "user-uploaded",
                "user uploaded",
                "我刚发",
                "我发的",
                "用户上传",
                "原图");
    }

    private boolean mentionsSingularImage(String text) {
        return containsAny(text,
                "that image",
                "this image",
                "the image",
                "that photo",
                "this photo",
                "刚才那张",
                "上一张",
                "这张",
                "那张",
                "这幅",
                "那幅",
                "这图",
                "那图");
    }

    private boolean hasUserAndGeneratedImages(List<ArchivedWechatImage> images) {
        boolean hasUser = images.stream().anyMatch(image -> image.sourceType() == ImageArchiveSourceType.USER_UPLOAD);
        boolean hasGenerated = images.stream().anyMatch(image -> image.sourceType() == ImageArchiveSourceType.AI_GENERATED);
        return hasUser && hasGenerated;
    }

    private List<ArchivedWechatImage> pendingImages(List<ArchivedWechatImage> images) {
        return images.stream()
                .filter(image -> "PENDING".equalsIgnoreCase(image.status()))
                .toList();
    }

    private List<ArchivedWechatImage> latestUserUploads(List<ArchivedWechatImage> images, int count) {
        if (count <= 0) {
            return List.of();
        }
        List<ArchivedWechatImage> uploads = images.stream()
                .filter(image -> image.sourceType() == ImageArchiveSourceType.USER_UPLOAD)
                .toList();
        if (uploads.isEmpty()) {
            return List.of();
        }
        int start = Math.max(0, uploads.size() - count);
        return uploads.subList(start, uploads.size());
    }

    private List<ArchivedWechatImage> latestUserUploadBatch(List<ArchivedWechatImage> images) {
        Optional<ArchivedWechatImage> latest = images.stream()
                .filter(image -> image.sourceType() == ImageArchiveSourceType.USER_UPLOAD)
                .max(Comparator.comparing(ArchivedWechatImage::createdAt));
        if (latest.isEmpty()) {
            return List.of();
        }
        String latestMessageId = latest.get().messageId();
        if (latestMessageId == null || latestMessageId.isBlank()) {
            return List.of(latest.get());
        }
        return images.stream()
                .filter(image -> image.sourceType() == ImageArchiveSourceType.USER_UPLOAD)
                .filter(image -> latestMessageId.equals(image.messageId()))
                .toList();
    }

    private Optional<ArchivedWechatImage> latestOf(List<ArchivedWechatImage> images, ImageArchiveSourceType sourceType) {
        return images.stream()
                .filter(image -> image.sourceType() == sourceType)
                .max(Comparator.comparing(ArchivedWechatImage::createdAt));
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
