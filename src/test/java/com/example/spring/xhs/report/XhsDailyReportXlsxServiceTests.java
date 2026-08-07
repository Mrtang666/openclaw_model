package com.example.spring.xhs.report;

import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.console.XhsConsoleUrlService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class XhsDailyReportXlsxServiceTests {

    @Test
    void generatesReadableWorkbookWithExpectedSheetsAndPostLink() throws Exception {
        XhsConsoleUrlService urls = mock(XhsConsoleUrlService.class);
        when(urls.postOpenUrl(42L)).thenReturn("http://127.0.0.1:8080/api/xhs-console/posts/42/open");
        Instant now = Instant.parse("2026-07-30T01:00:00Z");
        XhsDailyReport report = new XhsDailyReport(
                "brand-a", "品牌 A", LocalDate.of(2026, 7, 30), now.minusSeconds(86400), now,
                12, 10, 4, 2, 1, 2, 1, 3, 1, 48,
                List.of(new XhsRiskCategorySummary("产品质量", 2, 70, 88)),
                List.of(new XhsIncidentView(7, "brand-a", "过敏反馈", "产品质量", "OPEN",
                        88, "CRITICAL", 2, now, now)),
                List.of(new XhsReportPostSummary(42, "使用后发红", "用户反馈使用后不适",
                        "NEGATIVE", "评论负面反馈", 88, "评论", 55,
                        3, 88, 1, 70, now)));

        XhsDailyReportXlsxService.ReportWorkbook generated =
                new XhsDailyReportXlsxService().generate(report, urls);

        assertThat(generated.fileName()).isEqualTo("品牌_A-小红书舆情分析报告-2026-07-30.xlsx");
        assertThat(generated.bytes()).startsWith((byte) 'P', (byte) 'K');
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(generated.bytes()))) {
            assertThat(workbook.sheetIterator()).toIterable()
                    .extracting(sheet -> sheet.getSheetName())
                    .containsExactly("报告概览", "风险分类", "风险事件", "高风险笔记");
            assertThat(workbook.getSheet("报告概览").getRow(8).getCell(1).getStringCellValue())
                    .isEqualTo("2");
            assertThat(workbook.getSheet("高风险笔记").getRow(1).getCell(5).getStringCellValue())
                    .isEqualTo("评论");
            assertThat(workbook.getSheet("高风险笔记").getRow(1).getCell(7).getNumericCellValue())
                    .isEqualTo(3);
            assertThat(workbook.getSheet("高风险笔记").getRow(1).getCell(13).getHyperlink().getAddress())
                    .isEqualTo("http://127.0.0.1:8080/api/xhs-console/posts/42/open");
        }
    }
}
