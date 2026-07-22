package com.example.file;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;


/** Reads supported document formats and creates basic result files. */
public class FileDocumentService {

    private static final int MAX_EXTRACTED_CHARS = 16000;
    private static final String FILE_ROOT = "downloads/files";

    public Document readAndSave(String userId, String fileName, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IOException("文件为空");
        String safeName = sanitizeFileName(fileName);
        FileType type = FileType.fromName(safeName);
        if (type == FileType.UNSUPPORTED) {
            throw new IOException("暂不支持的文件格式: " + safeName);
        }
        if (bytes.length > 20 * 1024 * 1024) {
            throw new IOException("文件超过20MB限制");
        }

        Path dir = Paths.get(FILE_ROOT, sanitizePathPart(userId));
        Files.createDirectories(dir);
        Path path = dir.resolve(System.currentTimeMillis() + "_" + safeName);
        Files.write(path, bytes);
        try {
            String text = limit(extract(bytes, type));
            return new Document(safeName, type, path, text, bytes.length);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(path);
            throw e;
        }
    }

    public Path generate(String format, String title, String content) throws IOException {
        String normalized = normalizeFormat(format);
        if (!isGeneratedFormatSupported(normalized)) {
            throw new IOException("不支持生成格式: " + format);
        }
        Path path = Files.createTempFile("clawbot_generated_", "." + normalized);
        try {
            writeGenerated(path, normalized, content);
            return path;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(path);
            throw e;
        }
    }

    /** Generates a file under the user's file directory so it can be referenced later. */
    public Document generateAndSave(String userId, String format, String fileName,
                                    String content) throws IOException {
        String normalized = normalizeFormat(format);
        if (!isGeneratedFormatSupported(normalized)) {
            throw new IOException("不支持生成格式: " + format);
        }
        String safeName = sanitizeFileName(fileName);
        if (!safeName.toLowerCase(Locale.ROOT).endsWith("." + normalized)) {
            safeName += "." + normalized;
        }
        Path dir = Paths.get(FILE_ROOT, sanitizePathPart(userId));
        Files.createDirectories(dir);
        Path path = dir.resolve(System.currentTimeMillis() + "_" + safeName);
        try {
            writeGenerated(path, normalized, content);
            return new Document(safeName, FileType.fromName(safeName), path,
                    limit(content == null ? "" : content), Files.size(path));
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(path);
            throw e;
        }
    }

    private void writeGenerated(Path path, String normalized, String content) throws IOException {
        switch (normalized) {
            case "txt":
            case "md":
            case "json":
            case "csv":
                Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
                break;
            case "docx":
                writeDocx(path, content);
                break;
            case "xlsx":
                writeXlsx(path, content);
                break;
            case "pdf":
                writePdf(path, content);
                break;
            default:
                throw new IOException("不支持生成格式: " + normalized);
        }
    }

    private String extract(byte[] bytes, FileType type) throws IOException {
        switch (type) {
            case TXT:
            case MD:
            case JSON:
            case CSV:
                return decodeText(bytes);
            case PDF:
                try (PDDocument document = PDDocument.load(bytes)) {
                    return new PDFTextStripper().getText(document);
                }
            case DOCX:
                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                    StringBuilder text = new StringBuilder();
                    for (XWPFParagraph paragraph : document.getParagraphs()) {
                        text.append(paragraph.getText()).append('\n');
                    }
                    appendDocxTables(text, document.getTables());
                    return text.toString();
                }
            case XLSX:
                try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                    StringBuilder text = new StringBuilder();
                    DataFormatter formatter = new DataFormatter();
                    var evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                    for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                        XSSFSheet sheet = workbook.getSheetAt(s);
                        text.append("[工作表: ").append(sheet.getSheetName()).append("]\n");
                        for (Row row : sheet) {
                            boolean first = true;
                            for (var cell : row) {
                                if (!first) text.append('\t');
                                text.append(formatter.formatCellValue(cell, evaluator));
                                first = false;
                            }
                            text.append('\n');
                        }
                    }
                    return text.toString();
                }
            default:
                throw new IOException("不支持读取格式");
        }
    }

    private void appendDocxTables(StringBuilder text, java.util.List<XWPFTable> tables) {
        for (XWPFTable table : tables) {
            text.append("[表格]\n");
            for (XWPFTableRow row : table.getRows()) {
                boolean first = true;
                for (XWPFTableCell cell : row.getTableCells()) {
                    if (!first) text.append('\t');
                    text.append(cell.getText().replace('\n', ' '));
                    first = false;
                }
                text.append('\n');
            }
        }
    }

    private void writeDocx(Path path, String content) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            for (String line : splitLines(content)) {
                document.createParagraph().createRun().setText(line);
            }
            try (var output = Files.newOutputStream(path)) {
                document.write(output);
            }
        }
    }

    private void writeXlsx(Path path, String content) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("内容");
            int rowIndex = 0;
            for (String line : splitLines(content)) {
                var row = sheet.createRow(rowIndex++);
                String[] cells = line.split("\\t|,", -1);
                for (int i = 0; i < cells.length; i++) row.createCell(i).setCellValue(cells[i]);
            }
            try (var output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
    }

    private void writePdf(Path path, String content) throws IOException {
        if (content != null && content.indexOf('\uFFFD') >= 0) {
            throw new IOException("文档内容包含无法识别的替换字符，请重新生成");
        }
        try (PDDocument document = new PDDocument()) {
            byte[] fontBytes = loadChineseFont();
            if (fontBytes != null) {
                PDType0Font font = PDType0Font.load(document, new ByteArrayInputStream(fontBytes));
                writePdfTextPage(document, font, content);
            } else {
                writePdfImagePage(document, content);
            }
            document.save(path.toFile());
        }
    }

    private void writePdfTextPage(PDDocument document, PDType0Font font, String content) throws IOException {
        java.util.List<String> lines = wrapLines(content, 48);
        int index = 0;
        while (index < lines.size() || index == 0) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                boolean textStarted = false;
                try {
                    stream.beginText();
                    textStarted = true;
                    stream.setFont(font, 11);
                    stream.newLineAtOffset(45, 800);
                    int lineCount = 0;
                    while (index < lines.size() && lineCount < 46) {
                        stream.showText(lines.get(index++));
                        stream.newLineAtOffset(0, -16);
                        lineCount++;
                    }
                } finally {
                    if (textStarted) stream.endText();
                }
            }
            if (index >= lines.size()) break;
        }
    }

    private void writePdfImagePage(PDDocument document, String content) throws IOException {
        BufferedImage image = new BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 22));
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int y = 55;
        for (String line : splitLines(content)) {
            graphics.drawString(line.length() > 45 ? line.substring(0, 45) : line, 45, y);
            y += 32;
            if (y > 1700) break;
        }
        graphics.dispose();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        var imageObject = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, image);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.drawImage(imageObject, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
        }
    }

    private byte[] loadChineseFont() {
        for (String candidate : new String[]{
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simkai.ttf",
                "C:/Windows/Fonts/simfang.ttf",
                "C:/Windows/Fonts/simsunb.ttf"
        }) {
            try {
                Path path = Paths.get(candidate);
                if (Files.exists(path)) return Files.readAllBytes(path);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String decodeText(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') >= 0) return new String(bytes, Charset.forName("GB18030"));
        return utf8;
    }

    private String[] splitLines(String content) {
        return (content == null ? "" : content).replace("\r\n", "\n").split("\n", -1);
    }

    private java.util.List<String> wrapLines(String content, int maxChars) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String line : splitLines(content)) {
            if (line.isEmpty()) {
                result.add("");
                continue;
            }
            for (int start = 0; start < line.length(); start += maxChars) {
                result.add(line.substring(start, Math.min(start + maxChars, line.length())));
            }
        }
        return result;
    }

    private boolean isGeneratedFormatSupported(String format) {
        return format.equals("txt") || format.equals("md") || format.equals("json")
                || format.equals("csv") || format.equals("docx") || format.equals("xlsx")
                || format.equals("pdf");
    }

    private String normalizeFormat(String format) {
        String value = format == null ? "txt" : format.toLowerCase(Locale.ROOT).replace(".", "");
        if (value.equals("word")) return "docx";
        if (value.equals("excel")) return "xlsx";
        return value;
    }

    private String limit(String value) {
        if (value == null) return "";
        return value.length() <= MAX_EXTRACTED_CHARS ? value : value.substring(0, MAX_EXTRACTED_CHARS) + "\n[文件内容已截断]";
    }

    private String sanitizeFileName(String value) {
        String name = value == null || value.isBlank() ? "unknown_file" : value;
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String sanitizePathPart(String value) {
        return sanitizeFileName(value).replaceAll("[^A-Za-z0-9_@.-]", "_");
    }

    public enum FileType {
        TXT, MD, JSON, CSV, PDF, DOCX, XLSX, UNSUPPORTED;

        public static FileType fromName(String name) {
            String value = name == null ? "" : name.toLowerCase(Locale.ROOT);
            if (value.endsWith(".txt")) return TXT;
            if (value.endsWith(".md")) return MD;
            if (value.endsWith(".json")) return JSON;
            if (value.endsWith(".csv")) return CSV;
            if (value.endsWith(".pdf")) return PDF;
            if (value.endsWith(".docx")) return DOCX;
            if (value.endsWith(".xlsx")) return XLSX;
            return UNSUPPORTED;
        }
    }

    public static class Document {
        private final String fileName;
        private final FileType type;
        private final Path path;
        private final String text;
        private final long size;

        public Document(String fileName, FileType type, Path path, String text, long size) {
            this.fileName = fileName;
            this.type = type;
            this.path = path;
            this.text = text;
            this.size = size;
        }

        public String getFileName() { return fileName; }
        public FileType getType() { return type; }
        public Path getPath() { return path; }
        public String getText() { return text; }
        public long getSize() { return size; }
    }
}
