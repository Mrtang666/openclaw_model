package com.example.spring.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocumentGenerationServiceTests {
    @Test
    void createsReadableWordAndChinesePdf() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        Path font = Path.of("C:/Windows/Fonts/simhei.ttf");
        if (Files.isRegularFile(font)) properties.setPdfFontPath(font);
        DocumentGenerationService service = new DocumentGenerationService(properties);

        GeneratedDocument word = service.createWord(
            "项目总结", "第一部分\n文件功能已经完成。", "summary.docx");
        try (XWPFDocument document = new XWPFDocument(
            new ByteArrayInputStream(word.data()))) {
            assertThat(document.getParagraphs()).extracting(p -> p.getText())
                .contains("项目总结", "第一部分", "文件功能已经完成。");
        }

        GeneratedDocument pdf = service.createPdf(
            "项目总结", "**一、重点**\n- 文件功能已经完成。", "summary.pdf");
        try (PDDocument document = PDDocument.load(pdf.data())) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("一、重点", "文件功能已经完成")
                .doesNotContain("**");
        }
    }
}
