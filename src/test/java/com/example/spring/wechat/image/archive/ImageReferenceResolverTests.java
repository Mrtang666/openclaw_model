package com.example.spring.wechat.image.archive;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImageReferenceResolverTests {

    private final ImageReferenceResolver resolver = new ImageReferenceResolver();

    @Test
    void selectsExplicitImageIndex() {
        ImageReferenceResolution resolution = resolver.resolve(
                "please describe the second image",
                List.of(image(1, ImageArchiveSourceType.USER_UPLOAD), image(2, ImageArchiveSourceType.USER_UPLOAD)));

        assertThat(resolution.needsClarification()).isFalse();
        assertThat(resolution.selectedImages()).extracting(ArchivedWechatImage::imageIndex).containsExactly(2);
    }

    @Test
    void selectsLatestGeneratedImageWhenUserMentionsAssistantGeneratedImage() {
        ImageReferenceResolution resolution = resolver.resolve(
                "modify the image you just generated",
                List.of(
                        image(1, ImageArchiveSourceType.USER_UPLOAD),
                        image(2, ImageArchiveSourceType.AI_GENERATED),
                        image(3, ImageArchiveSourceType.USER_UPLOAD)));

        assertThat(resolution.needsClarification()).isFalse();
        assertThat(resolution.selectedImages()).extracting(ArchivedWechatImage::imageIndex).containsExactly(2);
    }

    @Test
    void asksClarificationWhenSingularReferenceCanPointToUploadOrGeneratedImage() {
        ImageReferenceResolution resolution = resolver.resolve(
                "modify that image",
                List.of(
                        image(1, ImageArchiveSourceType.USER_UPLOAD),
                        image(2, ImageArchiveSourceType.AI_GENERATED)));

        assertThat(resolution.needsClarification()).isTrue();
        assertThat(resolution.clarificationQuestion()).contains("用户上传").contains("AI 生成");
        assertThat(resolution.selectedImages()).isEmpty();
    }

    @Test
    void selectsPendingImagesInsteadOfAllHistoricalImagesWhenUserSaysTheseImages() {
        java.util.ArrayList<ArchivedWechatImage> images = new java.util.ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            images.add(image(index, "old-msg-" + index, ImageArchiveSourceType.USER_UPLOAD, "USED"));
        }
        images.add(image(21, "new-msg", ImageArchiveSourceType.USER_UPLOAD, "PENDING"));
        images.add(image(22, "new-msg", ImageArchiveSourceType.USER_UPLOAD, "PENDING"));

        ImageReferenceResolution resolution = resolver.resolve(
                "compare these images and generate a pdf",
                images);

        assertThat(resolution.needsClarification()).isFalse();
        assertThat(resolution.selectedImages()).extracting(ArchivedWechatImage::imageIndex).containsExactly(21, 22);
    }

    @Test
    void selectsLatestUploadBatchWhenUserMentionsTheseTwoImagesAfterTheyWereUsed() {
        java.util.ArrayList<ArchivedWechatImage> images = new java.util.ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            images.add(image(index, "old-msg-" + index, ImageArchiveSourceType.USER_UPLOAD, "USED"));
        }
        images.add(image(21, "new-msg", ImageArchiveSourceType.USER_UPLOAD, "USED"));
        images.add(image(22, "new-msg", ImageArchiveSourceType.USER_UPLOAD, "USED"));

        ImageReferenceResolution resolution = resolver.resolve(
                "compare these two images and generate a pdf",
                images);

        assertThat(resolution.needsClarification()).isFalse();
        assertThat(resolution.selectedImages()).extracting(ArchivedWechatImage::imageIndex).containsExactly(21, 22);
    }

    private ArchivedWechatImage image(int index, ImageArchiveSourceType sourceType) {
        return image(index, "msg-" + index, sourceType, index == 1 ? "USED" : "PENDING");
    }

    private ArchivedWechatImage image(
            int index,
            String messageId,
            ImageArchiveSourceType sourceType,
            String status) {
        return new ArchivedWechatImage(
                (long) index,
                "user-1",
                messageId,
                sourceType,
                "source-" + index,
                index,
                "image-" + index + ".png",
                "image/png",
                "sha-" + index,
                "md5-" + index,
                index,
                "",
                "",
                sourceType == ImageArchiveSourceType.AI_GENERATED ? "generated prompt" : "",
                "",
                status,
                Instant.parse("2026-07-22T00:00:%02dZ".formatted(index)),
                null);
    }
}
