package com.example.spring.xhs.report;

import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.console.XhsConsoleUrlService;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class XhsDailyReportDocxService {

    public static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String FONT = "Microsoft YaHei";
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ReportDocument generate(XhsDailyReport report, XhsConsoleUrlService consoleUrlService) {
        if (report == null) {
            throw new IllegalArgumentException("日报数据不能为空");
        }
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            title(document, report.projectName() + " 小红书舆情日报");
            centered(document, "项目标识：" + report.projectKey() + "    日报日期：" + report.reportDate(), 10);
            centered(document, "统计范围：" + format(report.periodStart()) + " 至 " + format(report.periodEnd()), 10);

            heading(document, "一、核心指标");
            metricTable(document, report);

            heading(document, "二、风险分类");
            categoryTable(document, report.categories());

            heading(document, "三、重点风险事件");
            incidentTable(document, report.topActiveIncidents());

            heading(document, "四、当日高风险笔记");
            postTable(document, report.topRiskPosts(), consoleUrlService);

            paragraph(document, "说明：本日报仅反映所选项目在统计时段内已采集并完成分析的数据。原帖链接需要本项目服务正在运行，并可能受到小红书登录或扫码验证限制。", 9);
            document.write(output);
            String fileName = safeFileName(report.projectName()) + "-小红书舆情日报-" + report.reportDate() + ".docx";
            return new ReportDocument(output.toByteArray(), fileName, DOCX_CONTENT_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Word 日报生成失败", exception);
        }
    }

    private void metricTable(XWPFDocument document, XhsDailyReport report) {
        XWPFTable table = document.createTable(4, 4);
        metricRow(table, 0, "新增帖子", report.collectedPosts(), "已分析", report.analyzedPosts());
        metricRow(table, 1, "负面帖子", report.negativePosts(), "高风险帖子", report.highRiskPosts());
        metricRow(table, 2, "新增事件", report.newIncidents(), "活跃事件", report.activeIncidents());
        metricRow(table, 3, "已解决事件", report.resolvedIncidents(), "平均风险分", report.averageRiskScore());
    }

    private void metricRow(XWPFTable table, int row, String firstLabel, int firstValue,
                           String secondLabel, int secondValue) {
        cell(table, row, 0, firstLabel, true);
        cell(table, row, 1, Integer.toString(firstValue), false);
        cell(table, row, 2, secondLabel, true);
        cell(table, row, 3, Integer.toString(secondValue), false);
    }

    private void categoryTable(XWPFDocument document, List<XhsRiskCategorySummary> categories) {
        XWPFTable table = document.createTable(Math.max(1, categories.size()) + 1, 4);
        header(table, "风险类别", "帖子数", "平均风险分", "最高风险分");
        if (categories.isEmpty()) {
            mergeEmptyRow(table, 1, "当日暂无风险分类数据");
            return;
        }
        for (int index = 0; index < categories.size(); index++) {
            XhsRiskCategorySummary item = categories.get(index);
            int row = index + 1;
            cell(table, row, 0, safe(item.riskCategory()), false);
            cell(table, row, 1, Integer.toString(item.postCount()), false);
            cell(table, row, 2, Integer.toString(item.averageRiskScore()), false);
            cell(table, row, 3, Integer.toString(item.maximumRiskScore()), false);
        }
    }

    private void incidentTable(XWPFDocument document, List<XhsIncidentView> incidents) {
        XWPFTable table = document.createTable(Math.max(1, incidents.size()) + 1, 5);
        header(table, "事件", "类别", "状态", "风险分", "关联帖子");
        if (incidents.isEmpty()) {
            mergeEmptyRow(table, 1, "当前没有未解决的风险事件");
            return;
        }
        for (int index = 0; index < incidents.size(); index++) {
            XhsIncidentView item = incidents.get(index);
            int row = index + 1;
            cell(table, row, 0, safe(item.title()), false);
            cell(table, row, 1, safe(item.riskCategory()), false);
            cell(table, row, 2, safe(item.status()), false);
            cell(table, row, 3, Integer.toString(item.riskScore()), false);
            cell(table, row, 4, Integer.toString(item.postCount()), false);
        }
    }

    private void postTable(XWPFDocument document, List<XhsReportPostSummary> posts,
                           XhsConsoleUrlService consoleUrlService) {
        XWPFTable table = document.createTable(Math.max(1, posts.size()) + 1, 5);
        header(table, "笔记", "情感", "风险类别", "风险分", "摘要");
        if (posts.isEmpty()) {
            mergeEmptyRow(table, 1, "当日暂无已分析笔记");
            return;
        }
        for (int index = 0; index < posts.size(); index++) {
            XhsReportPostSummary item = posts.get(index);
            int row = index + 1;
            hyperlinkCell(table.getRow(row).getCell(0),
                    safe(item.title()).isBlank() ? "无标题笔记" : item.title(),
                    consoleUrlService.postOpenUrl(item.postId()));
            cell(table, row, 1, safe(item.sentiment()), false);
            cell(table, row, 2, safe(item.riskCategory()), false);
            cell(table, row, 3, Integer.toString(item.riskScore()), false);
            cell(table, row, 4, safe(item.summary()), false);
        }
    }

    private void title(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        run(paragraph, text, 18, true);
    }

    private void centered(XWPFDocument document, String text, int size) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        run(paragraph, text, size, false);
    }

    private void heading(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(220);
        paragraph.setSpacingAfter(100);
        run(paragraph, text, 14, true);
    }

    private void paragraph(XWPFDocument document, String text, int size) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(180);
        run(paragraph, text, size, false);
    }

    private void header(XWPFTable table, String... values) {
        for (int index = 0; index < values.length; index++) {
            cell(table, 0, index, values[index], true);
        }
    }

    private void cell(XWPFTable table, int row, int column, String text, boolean bold) {
        XWPFTableCell cell = table.getRow(row).getCell(column);
        cell.removeParagraph(0);
        run(cell.addParagraph(), safe(text), 10, bold);
    }

    private void hyperlinkCell(XWPFTableCell cell, String text, String url) {
        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        XWPFHyperlinkRun link = paragraph.createHyperlinkRun(url);
        link.setText(text);
        link.setColor("2468B4");
        link.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
        link.setFontFamily(FONT);
        link.setFontSize(10);
    }

    private void mergeEmptyRow(XWPFTable table, int row, String text) {
        cell(table, row, 0, text, false);
        for (int index = 1; index < table.getRow(row).getTableCells().size(); index++) {
            cell(table, row, index, "", false);
        }
    }

    private XWPFRun run(XWPFParagraph paragraph, String text, int size, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.setFontFamily(FONT);
        run.setFontSize(size);
        run.setBold(bold);
        run.setText(safe(text));
        return run;
    }

    private String format(java.time.Instant value) {
        return value == null ? "-" : DATE_TIME.format(value.atZone(REPORT_ZONE));
    }

    private String safeFileName(String value) {
        String name = safe(value).replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
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
