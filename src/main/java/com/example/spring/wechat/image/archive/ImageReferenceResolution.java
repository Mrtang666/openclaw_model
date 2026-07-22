package com.example.spring.wechat.image.archive;

import java.util.List;

/**
 * 图片引用解析结果。
 *
 * <p>主流程只需要关心两件事：是否已经选中图片、是否需要先向用户追问。
 * 具体的“第几张、刚发的图、AI 生成的图”等判断逻辑封装在 {@link ImageReferenceResolver} 中。</p>
 */
public record ImageReferenceResolution(
        List<ArchivedWechatImage> selectedImages,
        boolean needsClarification,
        String clarificationQuestion) {

    public ImageReferenceResolution {
        selectedImages = selectedImages == null ? List.of() : List.copyOf(selectedImages);
        clarificationQuestion = clarificationQuestion == null ? "" : clarificationQuestion.strip();
    }

    public static ImageReferenceResolution selected(List<ArchivedWechatImage> images) {
        return new ImageReferenceResolution(images, false, "");
    }

    public static ImageReferenceResolution none() {
        return new ImageReferenceResolution(List.of(), false, "");
    }

    public static ImageReferenceResolution clarification(String question) {
        return new ImageReferenceResolution(List.of(), true, question);
    }

    public boolean hasSelection() {
        return !selectedImages.isEmpty();
    }
}
