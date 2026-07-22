package com.example.spring.wechat.image.archive;

import com.example.spring.chat.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageReferenceSemanticResolverTests {

    @Test
    void selectsImagesFromModelJsonAndValidatesIndexesAgainstAvailableImages() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("图片引用语义解析器"))).thenReturn("""
                {
                  "selected_image_indexes": [2, 999],
                  "needs_clarification": false,
                  "clarification_question": "",
                  "reason": "用户说红色背景那张，最符合第2张。"
                }
                """);
        ImageReferenceSemanticResolver resolver = new ImageReferenceSemanticResolver(chatService, new ObjectMapper());

        ImageReferenceResolution resolution = resolver.resolve(
                "把红色背景那张放进 PDF",
                List.of(
                        image(1, "blue-background.png", "蓝色背景"),
                        image(2, "red-background.png", "红色背景")));

        assertThat(resolution.needsClarification()).isFalse();
        assertThat(resolution.selectedImages()).extracting(ArchivedWechatImage::imageIndex).containsExactly(2);
    }

    @Test
    void returnsClarificationWhenModelCannotDetermineImageReference() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("图片引用语义解析器"))).thenReturn("""
                {
                  "selected_image_indexes": [],
                  "needs_clarification": true,
                  "clarification_question": "你想使用用户上传的图片，还是 AI 生成的图片？"
                }
                """);
        ImageReferenceSemanticResolver resolver = new ImageReferenceSemanticResolver(chatService, new ObjectMapper());

        ImageReferenceResolution resolution = resolver.resolve(
                "处理那张图",
                List.of(
                        image(1, "upload.png", "用户上传"),
                        generatedImage(2, "generated.png", "AI 生成")));

        assertThat(resolution.needsClarification()).isTrue();
        assertThat(resolution.clarificationQuestion()).contains("用户上传").contains("AI 生成");
        assertThat(resolution.selectedImages()).isEmpty();
    }

    private ArchivedWechatImage image(int index, String fileName, String description) {
        return new ArchivedWechatImage(
                (long) index,
                "user-1",
                "msg-" + index,
                ImageArchiveSourceType.USER_UPLOAD,
                "source-" + index,
                index,
                fileName,
                "image/png",
                "sha-" + index,
                "md5-" + index,
                index,
                "",
                "",
                "",
                description,
                "USED",
                Instant.parse("2026-07-22T00:00:%02dZ".formatted(index)),
                null);
    }

    private ArchivedWechatImage generatedImage(int index, String fileName, String prompt) {
        return new ArchivedWechatImage(
                (long) index,
                "user-1",
                "msg-" + index,
                ImageArchiveSourceType.AI_GENERATED,
                "source-" + index,
                index,
                fileName,
                "image/png",
                "sha-" + index,
                "md5-" + index,
                index,
                "",
                "",
                prompt,
                "",
                "USED",
                Instant.parse("2026-07-22T00:00:%02dZ".formatted(index)),
                null);
    }
}
