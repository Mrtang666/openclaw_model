package com.example.spring.xhs.report;

import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.console.XhsConsoleUrlService;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class XhsDailyReportDocxServiceTests {

    @Test
    void generatesReadableWordReportWithRiskDetails() throws Exception {
        XhsConsoleUrlService urls = mock(XhsConsoleUrlService.class);
        when(urls.postOpenUrl(42L)).thenReturn("http://127.0.0.1:8080/api/xhs-console/posts/42/open");
        XhsDailyReport report = new XhsDailyReport(
                "brand-a", "品牌 A", LocalDate.of(2026, 7, 29),
                Instant.parse("2026-07-28T16:00:00Z"), Instant.parse("2026-07-29T16:00:00Z"),
                12, 10, 4, 2, 1, 3, 1, 48,
                List.of(new XhsRiskCategorySummary("产品质量", 2, 70, 88)),
                List.of(new XhsIncidentView(7, "brand-a", "过敏反馈", "产品质量", "OPEN",
                        88, "CRITICAL", 2, Instant.now(), Instant.now())),
                List.of(new XhsReportPostSummary(42, "使用后发红", "用户反馈使用后不适",
                        "NEGATIVE", "产品质量", 88, Instant.now())));

        XhsDailyReportDocxService.ReportDocument generated =
                new XhsDailyReportDocxService().generate(report, urls);

        assertThat(generated.fileName()).isEqualTo("品牌 A-小红书舆情分析报告-2026-07-29.docx");
        assertThat(generated.contentType()).isEqualTo(XhsDailyReportDocxService.DOCX_CONTENT_TYPE);
        assertThat(generated.bytes()).startsWith((byte) 'P', (byte) 'K');
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(generated.bytes()));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            assertThat(extractor.getText())
                    .contains("品牌 A 小红书舆情分析报告", "核心指标", "产品质量", "过敏反馈", "使用后发红");
            assertThat(document.getDocument().getBody().isSetSectPr()).isTrue();
            assertThat(document.getTables()).allSatisfy(table ->
                    assertThat(table.getCTTbl().getTblPr().getTblW().getW().toString()).isEqualTo("9360"));
            assertThat(document.getTables().stream()
                    .flatMap(table -> table.getRows().stream())
                    .flatMap(row -> row.getTableCells().stream())
                    .flatMap(cell -> cell.getParagraphs().stream())
                    .flatMap(paragraph -> paragraph.getRuns().stream())
                    .filter(XWPFHyperlinkRun.class::isInstance)
                    .map(XWPFHyperlinkRun.class::cast)
                    .map(run -> run.getHyperlink(document))
                    .filter(java.util.Objects::nonNull)
                    .map(link -> link.getURL()))
                    .contains("http://127.0.0.1:8080/api/xhs-console/posts/42/open");
        }
        Path output = Path.of("target", "docx-qa", generated.fileName());
        Files.createDirectories(output.getParent());
        Files.write(output, generated.bytes());
    }
}
