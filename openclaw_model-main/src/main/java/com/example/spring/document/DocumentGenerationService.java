package com.example.spring.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

@Service
public class DocumentGenerationService {
    private static final String DOCX_MEDIA =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final DocumentProperties properties;

    public DocumentGenerationService(DocumentProperties properties) {
        this.properties = properties;
    }

    public GeneratedDocument createWord(String title, String content, String fileName)
        throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var section = document.getDocument().getBody().addNewSectPr();
            var margin = section.addNewPgMar();
            margin.setTop(1134);
            margin.setBottom(1134);
            margin.setLeft(1276);
            margin.setRight(1276);

            XWPFParagraph heading = document.createParagraph();
            heading.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = heading.createRun();
            styleRun(titleRun, 18, true);
            titleRun.setText(normalizeTitle(title));

            for (String paragraphText : paragraphs(content)) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.setSpacingAfter(120);
                XWPFRun run = paragraph.createRun();
                styleRun(run, 11, false);
                run.setText(paragraphText);
            }
            document.write(output);
            return new GeneratedDocument(output.toByteArray(), DOCX_MEDIA,
                ensureExtension(fileName, ".docx"), "Word 文档");
        }
    }

    public GeneratedDocument createPdf(String title, String content, String fileName)
        throws IOException {
        Path fontPath = resolvePdfFont();
        if (fontPath == null) {
            throw new IOException(
                "未找到可用中文字体，请在 .evn 配置 DOCUMENT_PDF_FONT_PATH，或改为输出 Word");
        }
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType0Font font = PDType0Font.load(document, fontPath.toFile());
            List<String> lines = new ArrayList<>();
            String pdfTitle = supportedText(font, normalizeTitle(title));
            lines.add(pdfTitle);
            lines.add("");
            for (String paragraph : paragraphs(content)) {
                lines.addAll(wrap(font, supportedText(font, paragraph), 11, 475));
                lines.add("");
            }
            PDPage page = null;
            PDPageContentStream stream = null;
            float y = 0;
            try {
                for (String line : lines) {
                    if (page == null || y < 60) {
                        if (stream != null) stream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        stream = new PDPageContentStream(document, page);
                        y = 790;
                    }
                    stream.beginText();
                    stream.setFont(font, line.equals(pdfTitle) ? 16 : 11);
                    stream.newLineAtOffset(60, y);
                    stream.showText(line);
                    stream.endText();
                    y -= line.isEmpty() ? 10 : 17;
                }
            } finally {
                if (stream != null) stream.close();
            }
            document.save(output);
            return new GeneratedDocument(output.toByteArray(), "application/pdf",
                ensureExtension(fileName, ".pdf"), "PDF 文档");
        }
    }

    private Path resolvePdfFont() {
        Path configured = properties.getPdfFontPath();
        if (configured != null && Files.isRegularFile(configured)) {
            return configured.toAbsolutePath().normalize();
        }
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return null;
        }
        for (String name : List.of("simhei.ttf", "Deng.ttf", "NotoSansSC-VF.ttf")) {
            Path candidate = Path.of("C:/Windows/Fonts", name);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    private static List<String> wrap(PDType0Font font, String text, float size, float width)
        throws IOException {
        if (text == null || text.isBlank()) return List.of("");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String next = new String(Character.toChars(codePoint));
            String candidate = current + next;
            if (!current.isEmpty() && font.getStringWidth(candidate) / 1000 * size > width) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(next);
            offset += Character.charCount(codePoint);
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    private static String supportedText(PDType0Font font, String text) {
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String value = new String(Character.toChars(codePoint));
            try {
                font.encode(value);
                result.append(value);
            } catch (IOException | IllegalArgumentException exception) {
                result.append('?');
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static List<String> paragraphs(String content) {
        String value = content == null ? "" : content.trim();
        if (value.isEmpty()) return List.of("（无内容）");
        return java.util.Arrays.stream(value.replace("\r\n", "\n").split("\\n+"))
            .map(DocumentGenerationService::cleanMarkup)
            .filter(line -> !line.isBlank())
            .toList();
    }

    private static String cleanMarkup(String line) {
        String value = line == null ? "" : line.trim();
        value = value.replaceFirst("^#{1,6}\\s*", "")
            .replace("**", "")
            .replace("__", "")
            .replaceFirst("^[-*•]\\s+", "- ");
        return value.trim();
    }

    private static void styleRun(XWPFRun run, int size, boolean bold) {
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(size);
        run.setBold(bold);
        run.getCTR().addNewRPr().addNewRFonts().setEastAsia("Microsoft YaHei");
    }

    private static String normalizeTitle(String title) {
        return title == null || title.isBlank() ? "文档处理结果" : title.trim();
    }

    private static String ensureExtension(String fileName, String extension) {
        String safe = DocumentExtractor.safeFileName(fileName);
        if (safe.toLowerCase(Locale.ROOT).endsWith(extension)) return safe;
        int dot = safe.lastIndexOf('.');
        if (dot > 0) safe = safe.substring(0, dot);
        return safe + extension;
    }
}
