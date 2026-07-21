package com.example.spring.wechat.document.service;

import com.example.spring.wechat.document.model.DocumentFormat;
import com.example.spring.wechat.document.model.GeneratedDocument;
import com.example.spring.wechat.document.model.GeneratedDocumentRequest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认文档生成服务。
 */
@Service
public class DefaultDocumentGenerationService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public GeneratedDocument generate(GeneratedDocumentRequest request, DocumentFormat format) {
        GeneratedDocumentRequest safeRequest = request == null
                ? new GeneratedDocumentRequest("未命名文档", "", "default")
                : request;
        DocumentFormat targetFormat = writableFormat(format);
        try {
            return switch (targetFormat) {
                case DOCX -> generateDocx(safeRequest);
                case PDF -> generatePdf(safeRequest);
                case MD -> generateMarkdown(safeRequest);
                case TXT -> generateTxt(safeRequest);
                default -> generateDocx(safeRequest);
            };
        } catch (IOException exception) {
            throw new IllegalArgumentException("文档生成失败：" + exception.getMessage(), exception);
        }
    }

    private DocumentFormat writableFormat(DocumentFormat format) {
        if (format == null || format == DocumentFormat.UNKNOWN || format == DocumentFormat.XLSX || format == DocumentFormat.PPTX) {
            return DocumentFormat.DOCX;
        }
        return format;
    }

    private GeneratedDocument generateDocx(GeneratedDocumentRequest request) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            titleRun.setText(request.title());

            for (String paragraphText : paragraphs(renderText(request))) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setFontSize(12);
                run.setText(paragraphText);
            }

            document.write(output);
            return new GeneratedDocument(
                    output.toByteArray(),
                    safeFileName(request.title(), DocumentFormat.DOCX),
                    DocumentFormat.DOCX,
                    DocumentFormat.DOCX.contentType(),
                    "文档已生成，请查收");
        }
    }

    private GeneratedDocument generatePdf(GeneratedDocumentRequest request) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont font = loadChineseFont(document);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            float y = 760;
            content.beginText();
            content.setFont(font, 16);
            content.newLineAtOffset(60, y);
            content.showText(safePdfText(request.title(), font));
            content.endText();
            y -= 36;

            content.beginText();
            content.setFont(font, 11);
            content.newLineAtOffset(60, y);
            for (String line : wrapForPdf(renderText(request), 42)) {
                if (y < 80) {
                    content.endText();
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = 760;
                    content.beginText();
                    content.setFont(font, 11);
                    content.newLineAtOffset(60, y);
                }
                content.showText(safePdfText(line, font));
                content.newLineAtOffset(0, -18);
                y -= 18;
            }
            content.endText();
            content.close();

            document.save(output);
            return new GeneratedDocument(
                    output.toByteArray(),
                    safeFileName(request.title(), DocumentFormat.PDF),
                    DocumentFormat.PDF,
                    DocumentFormat.PDF.contentType(),
                    "PDF 文档已生成，请查收");
        }
    }

    private GeneratedDocument generateTxt(GeneratedDocumentRequest request) {
        String text = request.title() + System.lineSeparator() + System.lineSeparator() + renderText(request);
        return new GeneratedDocument(
                text.getBytes(StandardCharsets.UTF_8),
                safeFileName(request.title(), DocumentFormat.TXT),
                DocumentFormat.TXT,
                DocumentFormat.TXT.contentType(),
                "TXT 文档已生成，请查收");
    }

    private GeneratedDocument generateMarkdown(GeneratedDocumentRequest request) {
        String markdown = "# " + request.title() + System.lineSeparator() + System.lineSeparator() + renderText(request);
        return new GeneratedDocument(
                markdown.getBytes(StandardCharsets.UTF_8),
                safeFileName(request.title(), DocumentFormat.MD),
                DocumentFormat.MD,
                DocumentFormat.MD.contentType(),
                "Markdown 文档已生成，请查收");
    }

    private String renderText(GeneratedDocumentRequest request) {
        if ("formal_report".equalsIgnoreCase(request.templateName())) {
            return """
                    一、工作概述

                    %s

                    二、当前进展

                    已根据需求整理核心内容，并生成可交付文档。

                    三、后续计划

                    后续可以继续补充数据、案例和更详细的排版要求。
                    """.formatted(request.content());
        }
        return request.content();
    }

    private List<String> paragraphs(String text) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        String[] values = text.split("\\R\\s*\\R");
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.strip());
            }
        }
        return result.isEmpty() ? List.of(text.strip()) : result;
    }

    private List<String> wrapForPdf(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : paragraphs(text)) {
            String[] logicalLines = paragraph.split("\\R", -1);
            for (String logicalLine : logicalLines) {
                String safeLine = logicalLine == null ? "" : logicalLine.strip();
                if (safeLine.isBlank()) {
                    lines.add("");
                    continue;
                }
                for (int start = 0; start < safeLine.length(); start += maxChars) {
                    lines.add(safeLine.substring(start, Math.min(safeLine.length(), start + maxChars)));
                }
            }
            lines.add("");
        }
        return lines;
    }

    private PDFont loadChineseFont(PDDocument document) throws IOException {
        for (String path : List.of(
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simsunb.ttf",
                "C:/Windows/Fonts/STSONG.TTF")) {
            File fontFile = new File(path);
            if (fontFile.isFile()) {
                return PDType0Font.load(document, fontFile);
            }
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private String safePdfText(String text, PDFont font) {
        String normalized = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        if (font instanceof PDType0Font) {
            return normalized;
        }
        return normalized.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String safeFileName(String title, DocumentFormat format) {
        String base = title == null || title.isBlank() ? "document" : title.strip();
        base = base.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        return base + "-" + LocalDateTime.now().format(FILE_TIME) + "." + format.extension();
    }
}
