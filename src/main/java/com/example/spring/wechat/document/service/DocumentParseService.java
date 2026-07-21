package com.example.spring.wechat.document.service;

import com.example.spring.wechat.document.model.DocumentChunk;
import com.example.spring.wechat.document.model.DocumentFormat;
import com.example.spring.wechat.document.model.ParsedDocument;
import com.example.spring.wechat.model.WechatIncomingFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档解析入口，负责把微信文件附件转换成可交给大模型理解的文本、摘要和分块。
 */
@Service
public class DocumentParseService {

    private static final String SCANNED_PDF_MESSAGE =
            "这个 PDF 可能是扫描版或图片型 PDF，当前第一版无法直接提取文字，后续可以接入 OCR 后支持。";

    private final DocumentTypeDetector typeDetector;
    private final DocumentChunkService chunkService;

    public DocumentParseService(DocumentTypeDetector typeDetector, DocumentChunkService chunkService) {
        this.typeDetector = typeDetector;
        this.chunkService = chunkService;
    }

    public static DocumentParseService defaultService() {
        return new DocumentParseService(new DocumentTypeDetector(), new DocumentChunkService(1200));
    }

    public ParsedDocument parse(WechatIncomingFile file) {
        if (file == null) {
            throw new IllegalArgumentException("缺少文件");
        }
        if (!file.hasBytes()) {
            return new ParsedDocument(
                    file.fileName(),
                    file.mimeType(),
                    DocumentFormat.fromExtension(file.fileName()),
                    "",
                    "文件内容暂时没有下载成功，请重新发送文件后再试。",
                    List.of());
        }

        byte[] bytes = file.bytes();
        DocumentFormat format = typeDetector.detect(file.fileName(), file.mimeType(), bytes);
        String text = extractText(format, bytes);
        if (format == DocumentFormat.PDF && text.isBlank()) {
            text = SCANNED_PDF_MESSAGE;
        }
        List<DocumentChunk> chunks = buildChunks(text);
        return new ParsedDocument(
                file.fileName(),
                file.mimeType(),
                format,
                text,
                buildSummary(text),
                chunks);
    }

    private String extractText(DocumentFormat format, byte[] bytes) {
        try {
            return switch (format) {
                case PDF -> extractPdf(bytes);
                case DOCX -> extractDocx(bytes);
                case XLSX -> extractXlsx(bytes);
                case PPTX -> extractPptx(bytes);
                case MD, TXT, UNKNOWN -> new String(bytes, StandardCharsets.UTF_8);
            };
        } catch (Exception exception) {
            throw new IllegalArgumentException("文档解析失败：" + exception.getMessage(), exception);
        }
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document).strip();
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText().strip();
        }
    }

    private String extractXlsx(byte[] bytes) throws IOException {
        StringBuilder output = new StringBuilder();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                var sheet = workbook.getSheetAt(sheetIndex);
                output.append("工作表：").append(sheet.getSheetName()).append(System.lineSeparator());
                int rowCount = 0;
                for (var row : sheet) {
                    List<String> cells = new ArrayList<>();
                    row.forEach(cell -> cells.add(cell.toString()));
                    if (!cells.isEmpty()) {
                        output.append(String.join(" | ", cells)).append(System.lineSeparator());
                        rowCount++;
                    }
                    if (rowCount >= 200) {
                        output.append("……该工作表内容较多，已截取前 200 行。").append(System.lineSeparator());
                        break;
                    }
                }
            }
        }
        return output.toString().strip();
    }

    private String extractPptx(byte[] bytes) throws IOException {
        StringBuilder output = new StringBuilder();
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            int slideIndex = 1;
            for (XSLFSlide slide : slideShow.getSlides()) {
                output.append("幻灯片 ").append(slideIndex++).append(System.lineSeparator());
                slide.getShapes().stream()
                        .filter(shape -> shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape)
                        .map(shape -> (org.apache.poi.xslf.usermodel.XSLFTextShape) shape)
                        .map(shape -> shape.getText().strip())
                        .filter(text -> !text.isBlank())
                        .forEach(text -> output.append(text).append(System.lineSeparator()));
            }
        }
        return output.toString().strip();
    }

    private List<DocumentChunk> buildChunks(String text) {
        List<String> rawChunks = chunkService.chunk(text);
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int index = 0; index < rawChunks.size(); index++) {
            String chunk = rawChunks.get(index);
            chunks.add(new DocumentChunk(index + 1, "片段 " + (index + 1), chunk, buildSummary(chunk)));
        }
        return chunks;
    }

    private String buildSummary(String text) {
        if (text == null || text.isBlank()) {
            return "文件没有提取到可读文本。";
        }
        String normalized = text.strip().replaceAll("\\s+", " ");
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }
}
