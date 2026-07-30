package com.example.spring.xhs.report;

import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.console.XhsConsoleUrlService;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class XhsDailyReportXlsxService {

    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Shanghai"));

    public ReportWorkbook generate(XhsDailyReport report, XhsConsoleUrlService consoleUrlService) {
        if (report == null) {
            throw new IllegalArgumentException("报告数据不能为空");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);
            overview(workbook, report, header);
            categories(workbook, report.categories(), header);
            incidents(workbook, report.topActiveIncidents(), header);
            posts(workbook, report.topRiskPosts(), consoleUrlService, header);
            workbook.write(output);
            String fileName = safeFileName(report.projectName()) + "-小红书舆情分析报告-"
                    + report.reportDate() + ".xlsx";
            return new ReportWorkbook(output.toByteArray(), fileName, XLSX_CONTENT_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Excel 报告生成失败", exception);
        }
    }

    private void overview(XSSFWorkbook workbook, XhsDailyReport report, CellStyle header) {
        Sheet sheet = workbook.createSheet("报告概览");
        String[][] rows = {
                {"项目名称", report.projectName()}, {"项目标识", report.projectKey()},
                {"报告日期", report.reportDate().toString()},
                {"统计开始", TIME.format(report.periodStart())}, {"统计结束", TIME.format(report.periodEnd())},
                {"采集帖子", Integer.toString(report.collectedPosts())},
                {"已分析", Integer.toString(report.analyzedPosts())},
                {"负面帖子", Integer.toString(report.negativePosts())},
                {"高风险帖子", Integer.toString(report.highRiskPosts())},
                {"新增事件", Integer.toString(report.newIncidents())},
                {"活跃事件", Integer.toString(report.activeIncidents())},
                {"已解决事件", Integer.toString(report.resolvedIncidents())},
                {"平均风险分", Integer.toString(report.averageRiskScore())}
        };
        for (int index = 0; index < rows.length; index++) {
            Row row = sheet.createRow(index);
            cell(row, 0, rows[index][0]).setCellStyle(header);
            cell(row, 1, rows[index][1]);
        }
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 45 * 256);
    }

    private void categories(XSSFWorkbook workbook, List<XhsRiskCategorySummary> values, CellStyle header) {
        Sheet sheet = workbook.createSheet("风险分类");
        header(sheet, header, "风险类别", "帖子数", "平均风险分", "最高风险分");
        for (int index = 0; index < values.size(); index++) {
            XhsRiskCategorySummary value = values.get(index);
            Row row = sheet.createRow(index + 1);
            cell(row, 0, value.riskCategory());
            number(row, 1, value.postCount());
            number(row, 2, value.averageRiskScore());
            number(row, 3, value.maximumRiskScore());
        }
        widths(sheet, 24, 14, 16, 16);
    }

    private void incidents(XSSFWorkbook workbook, List<XhsIncidentView> values, CellStyle header) {
        Sheet sheet = workbook.createSheet("风险事件");
        header(sheet, header, "事件编号", "标题", "类别", "状态", "风险等级", "风险分", "关联帖子", "最近发现");
        for (int index = 0; index < values.size(); index++) {
            XhsIncidentView value = values.get(index);
            Row row = sheet.createRow(index + 1);
            number(row, 0, value.incidentId());
            cell(row, 1, value.title());
            cell(row, 2, value.riskCategory());
            cell(row, 3, value.status());
            cell(row, 4, value.riskLevel());
            number(row, 5, value.riskScore());
            number(row, 6, value.postCount());
            cell(row, 7, value.lastSeenAt() == null ? "" : TIME.format(value.lastSeenAt()));
        }
        widths(sheet, 14, 48, 24, 18, 16, 12, 14, 22);
    }

    private void posts(XSSFWorkbook workbook, List<XhsReportPostSummary> values,
                       XhsConsoleUrlService consoleUrlService, CellStyle header) {
        Sheet sheet = workbook.createSheet("高风险笔记");
        header(sheet, header, "帖子编号", "标题", "情感", "风险类别", "风险分", "摘要", "发布时间", "原帖入口");
        for (int index = 0; index < values.size(); index++) {
            XhsReportPostSummary value = values.get(index);
            Row row = sheet.createRow(index + 1);
            number(row, 0, value.postId());
            cell(row, 1, value.title());
            cell(row, 2, value.sentiment());
            cell(row, 3, value.riskCategory());
            number(row, 4, value.riskScore());
            cell(row, 5, value.summary());
            cell(row, 6, value.publishedAt() == null ? "" : TIME.format(value.publishedAt()));
            Cell linkCell = cell(row, 7, "打开原帖");
            Hyperlink link = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
            link.setAddress(consoleUrlService.postOpenUrl(value.postId()));
            linkCell.setHyperlink(link);
        }
        widths(sheet, 14, 48, 14, 24, 12, 64, 22, 28);
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor((short) 22);
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void header(Sheet sheet, CellStyle style, String... values) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < values.length; index++) {
            cell(row, index, values[index]).setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, values.length - 1));
    }

    private Cell cell(Row row, int column, String value) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        return cell;
    }

    private void number(Row row, int column, long value) {
        row.createCell(column).setCellValue(value);
    }

    private void widths(Sheet sheet, int... widths) {
        for (int index = 0; index < widths.length; index++) {
            sheet.setColumnWidth(index, Math.min(widths[index], 100) * 256);
        }
    }

    private String safeFileName(String value) {
        String name = (value == null ? "" : value).replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return name.isBlank() ? "小红书项目" : name.substring(0, Math.min(name.length(), 40));
    }

    public record ReportWorkbook(byte[] bytes, String fileName, String contentType) {
        public ReportWorkbook {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
