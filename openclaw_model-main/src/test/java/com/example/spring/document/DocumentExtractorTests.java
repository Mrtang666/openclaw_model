package com.example.spring.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocumentExtractorTests {
    private final DocumentProperties properties = new DocumentProperties();
    private final DocumentExtractor extractor = new DocumentExtractor(properties);

    @Test
    void extractsUtf8TextAndDetectsTypeFromContent() throws Exception {
        var result = extractor.extract(
            "项目结论：文件功能可用。".getBytes(StandardCharsets.UTF_8), "report.txt");

        assertThat(result.mediaType()).startsWith("text/plain");
        assertThat(result.text()).contains("项目结论", "文件功能可用");
    }

    @Test
    void rejectsOversizedFilesBeforeParsing() {
        properties.setMaxUploadBytes(3);

        assertThatThrownBy(() -> extractor.extract(new byte[] {1, 2, 3, 4}, "a.txt"))
            .hasMessageContaining("超过大小限制");
    }

    @Test
    void extractsDocxXlsxPptxAndPdfWithoutTheFullTikaParserPackage() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Word 项目结论");
            document.write(output);
            docx = output.toByteArray();
        }
        assertThat(extractor.extract(docx, "report.docx").text()).contains("Word 项目结论");

        byte[] xlsx;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("数据").createRow(0).createCell(0).setCellValue("Excel 指标");
            workbook.write(output);
            xlsx = output.toByteArray();
        }
        assertThat(extractor.extract(xlsx, "data.xlsx").text()).contains("Excel 指标");

        byte[] pptx;
        try (XMLSlideShow show = new XMLSlideShow();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            show.createSlide().createTextBox().setText("PPT 汇报内容");
            show.write(output);
            pptx = output.toByteArray();
        }
        assertThat(extractor.extract(pptx, "slides.pptx").text()).contains("PPT 汇报内容");

        DocumentProperties pdfProperties = new DocumentProperties();
        pdfProperties.setPdfFontPath(Path.of("C:/Windows/Fonts/simhei.ttf"));
        byte[] pdf = new DocumentGenerationService(pdfProperties)
            .createPdf("PDF 标题", "PDF 正文内容", "result.pdf").data();
        assertThat(extractor.extract(pdf, "result.pdf").text()).contains("PDF", "正文内容");
    }
}
