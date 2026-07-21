package com.example.spring.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.rtf.RTFEditorKit;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import org.apache.tika.Tika;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;

@Service
public class DocumentExtractor {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/rtf",
        "text/rtf",
        "text/plain",
        "text/markdown",
        "text/csv",
        "text/html",
        "application/xhtml+xml");

    private final DocumentProperties properties;
    private final Tika tika = new Tika();

    public DocumentExtractor(DocumentProperties properties) {
        this.properties = properties;
    }

    public ExtractionResult extract(byte[] data, String fileName) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("文件内容为空");
        }
        if (data.length > properties.getMaxUploadBytes()) {
            throw new IOException("文件超过大小限制 "
                + properties.getMaxUploadBytes() / 1024 / 1024 + "MB");
        }
        String detected = resolveMediaType(
            normalizeMediaType(tika.detect(data, safeFileName(fileName))), fileName);
        if (!isSupported(detected, fileName)) {
            throw new IOException("暂不支持该文件类型：" + detected);
        }
        String text;
        try {
            text = normalizeText(switch (detected) {
                case "application/pdf" -> extractPdf(data);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    -> extractDocx(data);
                case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    -> extractXlsx(data);
                case "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    -> extractPptx(data);
                case "application/rtf", "text/rtf" -> extractRtf(data);
                case "text/html", "application/xhtml+xml" -> extractHtml(data);
                default -> decodeText(data);
            });
        } catch (Exception exception) {
            if (exception instanceof IOException ioException) throw ioException;
            throw new IOException("文件内容解析失败：" + exception.getMessage(), exception);
        }
        int limit = Math.max(1_000, properties.getMaxExtractedCharacters());
        if (text.length() > limit) text = text.substring(0, limit);
        if (text.isBlank()) {
            if ("application/pdf".equals(detected)) {
                throw new IOException("PDF 没有提取到可读文字，可能是扫描版 PDF；当前版本尚未启用 OCR");
            }
            throw new IOException("文件没有提取到可读文字");
        }
        return new ExtractionResult(detected, text);
    }

    private static String extractPdf(byte[] data) throws IOException {
        try (PDDocument document = PDDocument.load(data)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String extractDocx(byte[] data) throws IOException {
        configureZipLimits(data.length);
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(data))) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                appendLine(text, paragraph.getText());
            }
            for (XWPFTable table : document.getTables()) {
                table.getRows().forEach(row -> appendLine(text,
                    row.getTableCells().stream().map(cell -> cell.getText().trim())
                        .reduce((left, right) -> left + "\t" + right).orElse("")));
            }
        }
        return text.toString();
    }

    private static String extractXlsx(byte[] data) throws IOException {
        configureZipLimits(data.length);
        StringBuilder text = new StringBuilder();
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            for (Sheet sheet : workbook) {
                appendLine(text, "[工作表：" + sheet.getSheetName() + "]");
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    row.forEach(cell -> cells.add(formatter.formatCellValue(cell)));
                    appendLine(text, String.join("\t", cells));
                }
            }
        }
        return text.toString();
    }

    private static String extractPptx(byte[] data) throws IOException {
        configureZipLimits(data.length);
        StringBuilder text = new StringBuilder();
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(data))) {
            for (int index = 0; index < show.getSlides().size(); index++) {
                appendLine(text, "[第 " + (index + 1) + " 页]");
                for (XSLFShape shape : show.getSlides().get(index).getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        appendLine(text, textShape.getText());
                    }
                }
            }
        }
        return text.toString();
    }

    private static String extractRtf(byte[] data) throws Exception {
        DefaultStyledDocument document = new DefaultStyledDocument();
        new RTFEditorKit().read(new ByteArrayInputStream(data), document, 0);
        return document.getText(0, document.getLength());
    }

    private static String extractHtml(byte[] data) throws IOException {
        StringBuilder text = new StringBuilder();
        new ParserDelegator().parse(new StringReader(decodeText(data)),
            new HTMLEditorKit.ParserCallback() {
                @Override
                public void handleText(char[] value, int position) {
                    appendLine(text, new String(value));
                }
            }, true);
        return text.toString();
    }

    private static String decodeText(byte[] data) {
        int offset = data.length >= 3 && data[0] == (byte) 0xEF
            && data[1] == (byte) 0xBB && data[2] == (byte) 0xBF ? 3 : 0;
        return new String(data, offset, data.length - offset, StandardCharsets.UTF_8);
    }

    private static void configureZipLimits(int compressedBytes) {
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(Math.max(10L * 1024 * 1024, compressedBytes * 20L));
    }

    private static void appendLine(StringBuilder text, String value) {
        if (value == null || value.isBlank()) return;
        if (!text.isEmpty()) text.append('\n');
        text.append(value.trim());
    }

    private static boolean isSupported(String mediaType, String fileName) {
        if (SUPPORTED_TYPES.contains(mediaType)) {
            return true;
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return name.matches(".*\\.(txt|md|csv|html?|rtf|pdf|docx|xlsx|pptx)$");
    }

    static String safeFileName(String fileName) {
        String value = fileName == null ? "document" : fileName.trim();
        value = value.replace('\\', '_').replace('/', '_')
            .replaceAll("[\\p{Cntrl}:*?\"<>|]", "_");
        value = value.replaceAll("\\.{2,}", ".");
        return value.isBlank() ? "document" : value.substring(0, Math.min(120, value.length()));
    }

    private static String normalizeMediaType(String mediaType) {
        if (mediaType == null) {
            return "application/octet-stream";
        }
        int semicolon = mediaType.indexOf(';');
        return (semicolon >= 0 ? mediaType.substring(0, semicolon) : mediaType)
            .trim().toLowerCase(Locale.ROOT);
    }

    private static String resolveMediaType(String detected, String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".docx")) return
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (name.endsWith(".xlsx")) return
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (name.endsWith(".pptx")) return
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (name.endsWith(".rtf")) return "application/rtf";
        if (name.matches(".*\\.html?$")) return "text/html";
        if (name.endsWith(".md")) return "text/markdown";
        if (name.endsWith(".csv")) return "text/csv";
        if (name.endsWith(".txt")) return "text/plain";
        return detected;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.replace("\u0000", "")
            .replaceAll("[ \\t]+\\n", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    public record ExtractionResult(String mediaType, String text) {
    }
}
