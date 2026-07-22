package com.example.spring.wechat.conversation.tools;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.document.service.DefaultDocumentGenerationService;
import com.example.spring.wechat.document.service.DocumentParseService;
import com.example.spring.wechat.model.ImageSourceType;
import com.example.spring.wechat.model.WechatIncomingFile;
import com.example.spring.wechat.model.WechatIncomingImage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentWechatToolTests {

    @Test
    void documentAnalysisToolParsesIncomingFileAndReturnsSummary() {
        DocumentAnalysisWechatTool tool = new DocumentAnalysisWechatTool(DocumentParseService.defaultService());
        WechatIncomingFile file = new WechatIncomingFile(
                "wechat://1/file/1",
                "需求.md",
                "text/markdown",
                "# 项目目标\n\n实现微信端文档解析工具。".getBytes(StandardCharsets.UTF_8),
                null,
                null,
                null);

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1",
                "总结这个文件",
                Map.of("question", "总结这个文件"),
                "",
                List.of(),
                List.of(file),
                null,
                null));

        assertThat(reply.text()).contains("需求.md", "项目目标", "实现微信端文档解析工具");
    }

    @Test
    void documentGenerationToolUsesModelContentAndReturnsFilePart() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("请根据用户需求生成可以直接写入文档的正文")))
                .thenReturn("这是整理后的工作汇报正文。");
        DocumentGenerationWechatTool tool = new DocumentGenerationWechatTool(
                chatService,
                new DefaultDocumentGenerationService());

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1",
                "根据刚才的文件生成 Markdown 汇报",
                Map.of("format", "md", "title", "工作汇报", "template", "formal_report"),
                "最近文件：需求.md\n文件摘要：实现微信端文档解析工具。",
                List.of(),
                List.of(),
                null,
                null));

        assertThat(reply.parts()).hasSize(1);
        assertThat(reply.parts().get(0).hasFile()).isTrue();
        assertThat(reply.parts().get(0).file().fileName()).endsWith(".md");
        assertThat(new String(reply.parts().get(0).file().fileBytes(), StandardCharsets.UTF_8))
                .contains("工作汇报", "这是整理后的工作汇报正文");
    }

    @Test
    void documentGenerationToolIgnoresPlaceholderContentAndGeneratesFromContext() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(anyString())).thenReturn("""
                计组实验 4 实验报告总结

                本次实验围绕 cache 直接相联映射展开，重点完成了地址划分、命中判断、数据替换等内容。
                实验结果说明，合理设计 cache 映射策略可以提升访存效率。
                """);
        DocumentGenerationWechatTool tool = new DocumentGenerationWechatTool(
                chatService,
                new DefaultDocumentGenerationService());

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1",
                "总结一下然后给我以 pdf 的形式发给我",
                Map.of(
                        "format", "md",
                        "title", "计组实验4实验报告总结",
                        "content", "上一步总结的结果"),
                "最近文件：计组实验4实验报告.docx\n文件摘要：cache 直接相联映射、地址映射、替换算法。",
                List.of(),
                List.of(),
                null,
                null));

        assertThat(reply.parts()).hasSize(1);
        assertThat(reply.parts().get(0).hasFile()).isTrue();
        String generated = new String(reply.parts().get(0).file().fileBytes(), StandardCharsets.UTF_8);
        assertThat(generated)
                .contains("计组实验 4 实验报告总结", "cache 直接相联映射")
                .doesNotContain("上一步总结的结果");
        verify(chatService).reply(contains("最近文件：计组实验4实验报告.docx"));
    }

    @Test
    void documentGenerationToolCanPlaceWechatImagesIntoPdf() throws IOException {
        DocumentGenerationWechatTool tool = new DocumentGenerationWechatTool(
                mock(ChatService.class),
                new DefaultDocumentGenerationService());

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1",
                "帮我把这三张图片放在一个PDF文件中，并发给我",
                Map.of("format", "pdf", "title", "图片整理"),
                "当前可用图片资源：共 3 张",
                List.of(),
                List.of(),
                List.of(image("one.png"), image("two.png"), image("three.png")),
                null,
                null));

        assertThat(reply.parts()).hasSize(1);
        assertThat(reply.parts().get(0).hasFile()).isTrue();
        assertThat(reply.parts().get(0).file().fileName()).endsWith(".pdf");
        try (PDDocument document = Loader.loadPDF(reply.parts().get(0).file().fileBytes())) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void documentGenerationToolKeepsGeneratedTextWhenPdfAlsoContainsImages() throws IOException {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(anyString())).thenReturn("""
                Full dish analysis report

                Dish one: tomato beef, rich red color, balanced protein and vegetables.
                Dish two: steamed fish, light taste, suitable for a balanced meal.
                Overall advice: add green vegetables and reduce heavy oil.
                """);
        DocumentGenerationWechatTool tool = new DocumentGenerationWechatTool(
                chatService,
                new DefaultDocumentGenerationService());

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1",
                "Generate a complete PDF report for these dish photos",
                Map.of("format", "pdf", "title", "Dish Report", "requirement", "complete dish photo analysis"),
                "available images: 2",
                List.of(),
                List.of(),
                List.of(image("dish-one.png"), image("dish-two.png")),
                null,
                null));

        try (PDDocument document = Loader.loadPDF(reply.parts().get(0).file().fileBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("Full dish analysis report")
                    .contains("tomato beef")
                    .contains("steamed fish");
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(3);
        }
    }

    private WechatIncomingImage image(String fileName) throws IOException {
        return new WechatIncomingImage(
                ImageSourceType.WECHAT_ATTACHMENT,
                "wechat://" + fileName,
                samplePngBytes(),
                "image/png",
                fileName,
                20,
                20,
                "COLOR");
    }

    private byte[] samplePngBytes() throws IOException {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 20; x++) {
            for (int y = 0; y < 20; y++) {
                image.setRGB(x, y, (x + y) % 2 == 0 ? Color.ORANGE.getRGB() : Color.WHITE.getRGB());
            }
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
