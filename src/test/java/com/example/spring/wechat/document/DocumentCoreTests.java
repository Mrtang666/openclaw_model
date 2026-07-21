package com.example.spring.wechat.document;

import com.example.spring.wechat.document.model.DocumentFormat;
import com.example.spring.wechat.document.model.GeneratedDocument;
import com.example.spring.wechat.document.model.GeneratedDocumentRequest;
import com.example.spring.wechat.document.model.ParsedDocument;
import com.example.spring.wechat.document.service.DefaultDocumentGenerationService;
import com.example.spring.wechat.document.service.DocumentChunkService;
import com.example.spring.wechat.document.service.DocumentParseService;
import com.example.spring.wechat.document.service.DocumentTypeDetector;
import com.example.spring.wechat.model.WechatIncomingFile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DocumentCoreTests {

    @Test
    void detectsDocumentTypeFromMagicBytesAndFileName() {
        DocumentTypeDetector detector = new DocumentTypeDetector();

        assertThat(detector.detect("需求说明.pdf", "application/octet-stream", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(DocumentFormat.PDF);
        assertThat(detector.detect("说明.md", null, "# 标题".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(DocumentFormat.MD);
        assertThat(detector.detect("说明.txt", "text/plain", "普通文本".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(DocumentFormat.TXT);
    }

    @Test
    void parsesTextDocumentAndBuildsReadableChunks() {
        DocumentParseService parseService = DocumentParseService.defaultService();
        WechatIncomingFile file = new WechatIncomingFile(
                "wechat://1/file/1",
                "需求.md",
                "text/markdown",
                "# 项目目标\n\n第一段内容。\n\n## 计划\n\n第二段内容。".getBytes(StandardCharsets.UTF_8),
                null,
                null,
                null);

        ParsedDocument parsed = parseService.parse(file);

        assertThat(parsed.fileName()).isEqualTo("需求.md");
        assertThat(parsed.format()).isEqualTo(DocumentFormat.MD);
        assertThat(parsed.fullText()).contains("项目目标", "第一段内容", "计划");
        assertThat(parsed.summary()).contains("项目目标");
        assertThat(parsed.chunks()).isNotEmpty();
    }

    @Test
    void chunksLargeTextWithoutDroppingContent() {
        DocumentChunkService chunkService = new DocumentChunkService(40);
        String text = "第一段内容比较长，用来测试分块。\n\n第二段内容也比较长，用来确认不会丢失。";

        List<String> chunks = chunkService.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(String.join("", chunks)).contains("第一段内容", "第二段内容");
        assertThat(chunks).allMatch(chunk -> chunk.length() <= 40);
    }

    @Test
    void generatesDocxPdfTxtAndMarkdownDocuments() {
        DefaultDocumentGenerationService service = new DefaultDocumentGenerationService();
        GeneratedDocumentRequest request = new GeneratedDocumentRequest(
                "工作汇报",
                "本周完成了微信端文档工具设计和实现。",
                "formal_report");

        GeneratedDocument docx = service.generate(request, DocumentFormat.DOCX);
        GeneratedDocument pdf = service.generate(request, DocumentFormat.PDF);
        GeneratedDocument txt = service.generate(request, DocumentFormat.TXT);
        GeneratedDocument md = service.generate(request, DocumentFormat.MD);

        assertThat(docx.fileName()).endsWith(".docx");
        assertThat(docx.bytes()).isNotEmpty();
        assertThat(pdf.fileName()).endsWith(".pdf");
        assertThat(pdf.bytes()).isNotEmpty();
        assertThat(txt.fileName()).endsWith(".txt");
        assertThat(new String(txt.bytes(), StandardCharsets.UTF_8)).contains("工作汇报");
        assertThat(md.fileName()).endsWith(".md");
        assertThat(new String(md.bytes(), StandardCharsets.UTF_8)).contains("# 工作汇报");
    }

    @Test
    void generatesPdfWhenContentContainsSingleNewlines() {
        DefaultDocumentGenerationService service = new DefaultDocumentGenerationService();
        GeneratedDocumentRequest request = new GeneratedDocumentRequest(
                "全文总结",
                """
                        一、实验目的
                        掌握 cache 直接相联映射。
                        二、实验过程
                        完成地址划分、命中判断和数据替换。
                        """,
                "default");

        assertThatCode(() -> {
            GeneratedDocument pdf = service.generate(request, DocumentFormat.PDF);
            assertThat(pdf.fileName()).endsWith(".pdf");
            assertThat(pdf.bytes()).isNotEmpty();
        }).doesNotThrowAnyException();
    }
}
