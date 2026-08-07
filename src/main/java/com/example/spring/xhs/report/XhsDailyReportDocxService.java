package com.example.spring.xhs.report;

import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.console.XhsConsoleUrlService;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblCellMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class XhsDailyReportDocxService {

    public static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String FONT = "Microsoft YaHei";
    private static final String TITLE_COLOR = "17324D";
    private static final String HEADING_COLOR = "24506F";
    private static final String MUTED_COLOR = "64748B";
    private static final String HEADER_FILL = "E8EEF5";
    private static final String LABEL_FILL = "F2F4F7";
    private static final String BORDER_COLOR = "CBD5E1";
    private static final int TABLE_WIDTH = 9360;
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ReportDocument generate(XhsDailyReport report, XhsConsoleUrlService consoleUrlService) {
        if (report == null) {
            throw new IllegalArgumentException("日报数据不能为空");
        }
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configurePage(document);
            configureHeaderFooter(document, report);
            title(document, report.projectName() + " 小红书舆情分析报告");
            subtitle(document, "统计周期  " + format(report.periodStart()) + " 至 " + format(report.periodEnd()));
            metadataTable(document, report);

            heading(document, "核心指标");
            metricTable(document, report);

            heading(document, "风险分类");
            categoryTable(document, report.categories());

            heading(document, "重点风险事件");
            incidentTable(document, report.topActiveIncidents());

            heading(document, "高风险笔记");
            postTable(document, report.topRiskPosts(), consoleUrlService);

            note(document, "数据说明：本报告仅统计所选时间范围内已采集且已完成分析的数据。"
                    + "原帖链接需要管理服务处于运行状态，并可能受到小红书登录或验证限制。");
            document.write(output);
            String fileName = safeFileName(report.projectName()) + "-小红书舆情分析报告-"
                    + report.reportDate() + ".docx";
            return new ReportDocument(output.toByteArray(), fileName, DOCX_CONTENT_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Word 报告生成失败", exception);
        }
    }

    private void configurePage(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        size.setW(BigInteger.valueOf(12240));
        size.setH(BigInteger.valueOf(15840));
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1440));
        margins.setRight(BigInteger.valueOf(1440));
        margins.setBottom(BigInteger.valueOf(1440));
        margins.setLeft(BigInteger.valueOf(1440));
        margins.setHeader(BigInteger.valueOf(708));
        margins.setFooter(BigInteger.valueOf(708));
        margins.setGutter(BigInteger.ZERO);
    }

    private void configureHeaderFooter(XWPFDocument document, XhsDailyReport report) {
        XWPFHeader header = document.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph headerParagraph = header.getParagraphs().isEmpty()
                ? header.createParagraph() : header.getParagraphs().get(0);
        headerParagraph.setAlignment(ParagraphAlignment.LEFT);
        headerParagraph.setBorderBottom(org.apache.poi.xwpf.usermodel.Borders.SINGLE);
        styledRun(headerParagraph, "小红书舆情分析报告  |  " + report.projectName(), 8, false, MUTED_COLOR);

        XWPFFooter footer = document.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph footerParagraph = footer.getParagraphs().isEmpty()
                ? footer.createParagraph() : footer.getParagraphs().get(0);
        footerParagraph.setAlignment(ParagraphAlignment.RIGHT);
        styledRun(footerParagraph, "第 ", 8, false, MUTED_COLOR);
        pageField(footerParagraph);
        styledRun(footerParagraph, " 页", 8, false, MUTED_COLOR);
    }

    private void pageField(XWPFParagraph paragraph) {
        CTFldChar begin = paragraph.getCTP().addNewR().addNewFldChar();
        begin.setFldCharType(STFldCharType.BEGIN);
        CTText instruction = paragraph.getCTP().addNewR().addNewInstrText();
        instruction.setStringValue(" PAGE ");
        instruction.setSpace(org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute.Space.PRESERVE);
        CTFldChar end = paragraph.getCTP().addNewR().addNewFldChar();
        end.setFldCharType(STFldCharType.END);
    }

    private void title(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingAfter(80);
        paragraph.setKeepNext(true);
        styledRun(paragraph, text, 22, true, TITLE_COLOR);
    }

    private void subtitle(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(180);
        paragraph.setKeepNext(true);
        styledRun(paragraph, text, 10, false, MUTED_COLOR);
    }

    private void metadataTable(XWPFDocument document, XhsDailyReport report) {
        XWPFTable table = document.createTable(2, 4);
        configureTable(table, new int[]{1500, 3180, 1500, 3180});
        labelCell(table, 0, 0, "项目名称");
        valueCell(table, 0, 1, report.projectName(), false);
        labelCell(table, 0, 2, "项目标识");
        valueCell(table, 0, 3, report.projectKey(), false);
        labelCell(table, 1, 0, "报告日期");
        valueCell(table, 1, 1, report.reportDate().toString(), false);
        labelCell(table, 1, 2, "生成口径");
        valueCell(table, 1, 3, "已采集并完成分析", false);
        afterTable(document, 80);
    }

    private void metricTable(XWPFDocument document, XhsDailyReport report) {
        XWPFTable table = document.createTable(5, 4);
        configureTable(table, new int[]{1750, 2930, 1750, 2930});
        metricRow(table, 0, "采集帖子", report.collectedPosts(), "已分析", report.analyzedPosts());
        metricRow(table, 1, "负面帖子", report.negativePosts(), "高风险帖子", report.highRiskPosts());
        metricRow(table, 2, "含负面评论", report.negativeCommentPosts(),
                "含负面图片", report.negativeImagePosts());
        metricRow(table, 3, "新增事件", report.newIncidents(), "活跃事件", report.activeIncidents());
        metricRow(table, 4, "已解决事件", report.resolvedIncidents(), "平均风险分", report.averageRiskScore());
        afterTable(document, 60);
    }

    private void metricRow(XWPFTable table, int row, String firstLabel, int firstValue,
                           String secondLabel, int secondValue) {
        labelCell(table, row, 0, firstLabel);
        valueCell(table, row, 1, Integer.toString(firstValue), true);
        labelCell(table, row, 2, secondLabel);
        valueCell(table, row, 3, Integer.toString(secondValue), true);
    }

    private void categoryTable(XWPFDocument document, List<XhsRiskCategorySummary> categories) {
        XWPFTable table = document.createTable(Math.max(1, categories.size()) + 1, 4);
        configureTable(table, new int[]{3960, 1400, 2000, 2000});
        header(table, "风险类别", "帖子数", "平均风险分", "最高风险分");
        if (categories.isEmpty()) {
            emptyRow(table, 1, "统计周期内暂无风险分类数据", 4);
        } else {
            for (int index = 0; index < categories.size(); index++) {
                XhsRiskCategorySummary item = categories.get(index);
                int row = index + 1;
                valueCell(table, row, 0, safe(item.riskCategory()), false);
                centeredCell(table, row, 1, Integer.toString(item.postCount()));
                centeredCell(table, row, 2, Integer.toString(item.averageRiskScore()));
                centeredCell(table, row, 3, Integer.toString(item.maximumRiskScore()));
            }
        }
        afterTable(document, 60);
    }

    private void incidentTable(XWPFDocument document, List<XhsIncidentView> incidents) {
        XWPFTable table = document.createTable(Math.max(1, incidents.size()) + 1, 5);
        configureTable(table, new int[]{3300, 2100, 1400, 1200, 1360});
        header(table, "事件", "类别", "状态", "风险分", "关联帖子");
        if (incidents.isEmpty()) {
            emptyRow(table, 1, "当前没有未解决的风险事件", 5);
        } else {
            for (int index = 0; index < incidents.size(); index++) {
                XhsIncidentView item = incidents.get(index);
                int row = index + 1;
                valueCell(table, row, 0, safe(item.title()), false);
                valueCell(table, row, 1, safe(item.riskCategory()), false);
                centeredCell(table, row, 2, safe(item.status()));
                centeredCell(table, row, 3, Integer.toString(item.riskScore()));
                centeredCell(table, row, 4, Integer.toString(item.postCount()));
            }
        }
        afterTable(document, 60);
    }

    private void postTable(XWPFDocument document, List<XhsReportPostSummary> posts,
                           XhsConsoleUrlService consoleUrlService) {
        XWPFTable table = document.createTable(Math.max(1, posts.size()) + 1, 6);
        configureTable(table, new int[]{1900, 950, 1450, 800, 1600, 2660});
        header(table, "笔记标题", "情感", "风险类别", "风险分", "风险来源", "摘要");
        if (posts.isEmpty()) {
            emptyRow(table, 1, "统计周期内暂无已分析笔记", 6);
        } else {
            for (int index = 0; index < posts.size(); index++) {
                XhsReportPostSummary item = posts.get(index);
                int row = index + 1;
                hyperlinkCell(table.getRow(row).getCell(0),
                        safe(item.title()).isBlank() ? "无标题笔记" : item.title(),
                        consoleUrlService.postOpenUrl(item.postId()));
                centeredCell(table, row, 1, safe(item.sentiment()));
                valueCell(table, row, 2, safe(item.riskCategory()), false);
                centeredCell(table, row, 3, Integer.toString(item.riskScore()));
                valueCell(table, row, 4, riskDetails(item), false);
                valueCell(table, row, 5, safe(item.summary()), false);
            }
        }
        afterTable(document, 80);
    }

    private String riskDetails(XhsReportPostSummary item) {
        StringBuilder details = new StringBuilder(safe(item.riskSource()));
        if (item.negativeCommentCount() > 0) {
            details.append("\n负面评论 ").append(item.negativeCommentCount())
                    .append(" 条（最高 ").append(item.highestCommentRiskScore()).append(" 分）");
        }
        if (item.negativeImageCount() > 0) {
            details.append("\n负面图片 ").append(item.negativeImageCount())
                    .append(" 张（最高 ").append(item.highestImageRiskScore()).append(" 分）");
        }
        return details.toString();
    }

    private void heading(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(260);
        paragraph.setSpacingAfter(100);
        paragraph.setKeepNext(true);
        styledRun(paragraph, text, 15, true, HEADING_COLOR);
    }

    private void note(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(140);
        paragraph.setSpacingAfter(0);
        paragraph.setIndentationLeft(120);
        paragraph.setIndentationRight(120);
        styledRun(paragraph, text, 9, false, MUTED_COLOR);
    }

    private void afterTable(XWPFDocument document, int spacingAfter) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(spacingAfter);
        XWPFRun run = paragraph.createRun();
        run.setText("");
        run.setFontSize(1);
    }

    private void configureTable(XWPFTable table, int[] widths) {
        table.setTableAlignment(TableRowAlign.CENTER);
        table.setWidth(TABLE_WIDTH);
        CTTblPr properties = table.getCTTbl().getTblPr();
        CTTblWidth tableWidth = properties.isSetTblW() ? properties.getTblW() : properties.addNewTblW();
        tableWidth.setType(STTblWidth.DXA);
        tableWidth.setW(BigInteger.valueOf(TABLE_WIDTH));
        CTTblWidth indent = properties.isSetTblInd() ? properties.getTblInd() : properties.addNewTblInd();
        indent.setType(STTblWidth.DXA);
        indent.setW(BigInteger.valueOf(120));
        configureBorders(properties);
        configureCellMargins(properties);
        CTTblGrid grid = table.getCTTbl().getTblGrid();
        if (grid == null) {
            grid = table.getCTTbl().addNewTblGrid();
        }
        while (grid.sizeOfGridColArray() > 0) {
            grid.removeGridCol(0);
        }
        for (int width : widths) {
            grid.addNewGridCol().setW(BigInteger.valueOf(width));
        }
        for (XWPFTableRow row : table.getRows()) {
            row.setCantSplitRow(true);
            for (int index = 0; index < row.getTableCells().size() && index < widths.length; index++) {
                XWPFTableCell cell = row.getCell(index);
                cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
                CTTcPr cellProperties = cell.getCTTc().isSetTcPr()
                        ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
                CTTblWidth cellWidth = cellProperties.isSetTcW()
                        ? cellProperties.getTcW() : cellProperties.addNewTcW();
                cellWidth.setType(STTblWidth.DXA);
                cellWidth.setW(BigInteger.valueOf(widths[index]));
            }
        }
    }

    private void configureBorders(CTTblPr properties) {
        CTTblBorders borders = properties.isSetTblBorders()
                ? properties.getTblBorders() : properties.addNewTblBorders();
        border(borders.isSetTop() ? borders.getTop() : borders.addNewTop());
        border(borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft());
        border(borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom());
        border(borders.isSetRight() ? borders.getRight() : borders.addNewRight());
        border(borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH());
        border(borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV());
    }

    private void border(CTBorder border) {
        border.setVal(STBorder.SINGLE);
        border.setColor(BORDER_COLOR);
        border.setSz(BigInteger.valueOf(4));
    }

    private void configureCellMargins(CTTblPr properties) {
        CTTblCellMar margins = properties.isSetTblCellMar()
                ? properties.getTblCellMar() : properties.addNewTblCellMar();
        margin(margins.isSetTop() ? margins.getTop() : margins.addNewTop(), 100);
        margin(margins.isSetBottom() ? margins.getBottom() : margins.addNewBottom(), 100);
        margin(margins.isSetLeft() ? margins.getLeft() : margins.addNewLeft(), 120);
        margin(margins.isSetRight() ? margins.getRight() : margins.addNewRight(), 120);
    }

    private void margin(CTTblWidth margin, int width) {
        margin.setType(STTblWidth.DXA);
        margin.setW(BigInteger.valueOf(width));
    }

    private void header(XWPFTable table, String... values) {
        XWPFTableRow row = table.getRow(0);
        row.setRepeatHeader(true);
        for (int index = 0; index < values.length; index++) {
            setCell(table.getRow(0).getCell(index), values[index], true, ParagraphAlignment.CENTER, HEADER_FILL, 10);
        }
    }

    private void labelCell(XWPFTable table, int row, int column, String text) {
        setCell(table.getRow(row).getCell(column), text, true, ParagraphAlignment.LEFT, LABEL_FILL, 10);
    }

    private void valueCell(XWPFTable table, int row, int column, String text, boolean emphasized) {
        setCell(table.getRow(row).getCell(column), text, emphasized, ParagraphAlignment.LEFT, null,
                emphasized ? 12 : 10);
    }

    private void centeredCell(XWPFTable table, int row, int column, String text) {
        setCell(table.getRow(row).getCell(column), text, false, ParagraphAlignment.CENTER, null, 10);
    }

    private void setCell(XWPFTableCell cell, String text, boolean bold, ParagraphAlignment alignment,
                         String fill, int size) {
        clearParagraphs(cell);
        if (fill != null) {
            CTTcPr properties = cell.getCTTc().isSetTcPr()
                    ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
            CTShd shading = properties.isSetShd() ? properties.getShd() : properties.addNewShd();
            shading.setFill(fill);
        }
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setAlignment(alignment);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(1.1);
        styledRun(paragraph, safe(text), size, bold, TITLE_COLOR);
    }

    private void hyperlinkCell(XWPFTableCell cell, String text, String url) {
        clearParagraphs(cell);
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBetween(1.1);
        XWPFHyperlinkRun link = paragraph.createHyperlinkRun(url);
        link.setText(text);
        link.setColor("2468B4");
        link.setUnderline(UnderlinePatterns.SINGLE);
        applyFont(link, 10, false);
    }

    private void emptyRow(XWPFTable table, int rowIndex, String text, int columns) {
        XWPFTableCell first = table.getRow(rowIndex).getCell(0);
        CTTcPr properties = first.getCTTc().isSetTcPr()
                ? first.getCTTc().getTcPr() : first.getCTTc().addNewTcPr();
        properties.addNewGridSpan().setVal(BigInteger.valueOf(columns));
        for (int index = columns - 1; index > 0; index--) {
            table.getRow(rowIndex).removeCell(index);
        }
        setCell(first, text, false, ParagraphAlignment.CENTER, null, 10);
    }

    private void clearParagraphs(XWPFTableCell cell) {
        while (!cell.getParagraphs().isEmpty()) {
            cell.removeParagraph(0);
        }
    }

    private XWPFRun styledRun(XWPFParagraph paragraph, String text, int size, boolean bold, String color) {
        XWPFRun run = paragraph.createRun();
        run.setText(safe(text));
        run.setColor(color);
        applyFont(run, size, bold);
        return run;
    }

    private void applyFont(XWPFRun run, int size, boolean bold) {
        run.setFontFamily(FONT);
        run.setFontSize(size);
        run.setBold(bold);
        CTRPr properties = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        CTFonts fonts = properties.sizeOfRFontsArray() > 0
                ? properties.getRFontsArray(0) : properties.addNewRFonts();
        fonts.setAscii(FONT);
        fonts.setHAnsi(FONT);
        fonts.setEastAsia(FONT);
    }

    private String format(java.time.Instant value) {
        return value == null ? "-" : DATE_TIME.format(value.atZone(REPORT_ZONE));
    }

    private String safeFileName(String value) {
        String name = safe(value).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("[. ]+$", "");
        return name.isBlank() ? "小红书项目" : name.substring(0, Math.min(name.length(), 40));
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    public record ReportDocument(byte[] bytes, String fileName, String contentType) {
        public ReportDocument {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
