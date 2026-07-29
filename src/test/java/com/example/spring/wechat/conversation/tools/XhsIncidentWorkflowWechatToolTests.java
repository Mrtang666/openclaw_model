package com.example.spring.wechat.conversation.tools;

import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.incident.XhsIncidentStatus;
import com.example.spring.xhs.incident.XhsIncidentTransition;
import com.example.spring.xhs.incident.XhsIncidentWorkflowService;
import com.example.spring.xhs.report.XhsDailyReport;
import com.example.spring.xhs.report.XhsDailyReportService;
import com.example.spring.xhs.report.XhsRiskCategorySummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XhsIncidentWorkflowWechatToolTests {

    @Test
    void bindsIncidentTransitionToCurrentConnectionAndUser() {
        XhsIncidentWorkflowService service = mock(XhsIncidentWorkflowService.class);
        when(service.transition("brand-a", 42, "INVESTIGATING", "connection-1", "user-1", "核查中"))
                .thenReturn(new XhsIncidentTransition(
                        42, "brand-a", XhsIncidentStatus.ACKNOWLEDGED,
                        XhsIncidentStatus.INVESTIGATING, true, Instant.now()));
        XhsIncidentTransitionWechatTool tool = new XhsIncidentTransitionWechatTool(service);

        var reply = tool.execute(new WechatToolRequest(
                "connection-1:user-1", "开始调查事件",
                Map.of("project_key", "brand-a", "incident_id", "42",
                        "target_status", "INVESTIGATING", "note", "核查中"),
                "", List.of(), null, null));

        verify(service).transition("brand-a", 42, "INVESTIGATING", "connection-1", "user-1", "核查中");
        assertThat(reply.text()).contains("ACKNOWLEDGED -> INVESTIGATING", "审计日志");
    }

    @Test
    void reportContainsMetricsCategoriesAndActionableIncidentId() {
        XhsDailyReportService service = mock(XhsDailyReportService.class);
        Instant start = Instant.parse("2026-07-27T16:00:00Z");
        Instant end = Instant.parse("2026-07-28T16:00:00Z");
        when(service.report("brand-a", "2026-07-28", 5)).thenReturn(new XhsDailyReport(
                "brand-a", "品牌 A", LocalDate.of(2026, 7, 28), start, end,
                20, 18, 7, 3, 2, 4, 1, 46,
                List.of(new XhsRiskCategorySummary("CONSUMER_SAFETY", 3, 72, 88)),
                List.of(new XhsIncidentView(
                        42, "brand-a", "用户反馈红肿", "CONSUMER_SAFETY",
                        "INVESTIGATING", 88, "CRITICAL", 3, start, end)),
                List.of()));
        XhsDailyReportWechatTool tool = new XhsDailyReportWechatTool(service);

        var reply = tool.execute(new WechatToolRequest(
                "connection-1:user-1", "生成日报",
                Map.of("project_key", "brand-a", "date", "2026-07-28", "top_limit", "5"),
                "", List.of(), null, null));

        assertThat(reply.text()).contains(
                "采集笔记：20", "负面笔记：7", "当日解决事件：1",
                "CONSUMER_SAFETY", "#42", "仅覆盖已采集并完成分析的数据");
    }
}
