package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.report.XhsDailyReport;
import com.example.spring.xhs.report.XhsDailyReportService;
import com.example.spring.xhs.report.XhsRiskCategorySummary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class XhsDailyReportWechatTool implements WechatTool {

    private final XhsDailyReportService reportService;

    public XhsDailyReportWechatTool(XhsDailyReportService reportService) {
        this.reportService = reportService;
    }

    @Override
    public String name() {
        return "xhs_daily_report";
    }

    @Override
    public String description() {
        return "生成指定小红书舆情项目的自然日日报。";
    }

    @Override
    public List<String> arguments() {
        return List.of("project_key", "date", "top_limit");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("project_key", "舆情项目唯一标识", "brand-a"),
                WechatToolParameter.optionalString("date", "报告日期，默认今天，格式 yyyy-MM-dd", "2026-07-28"),
                WechatToolParameter.optionalString("top_limit", "高风险未结事件数量，默认 5，最多 10", "5"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "生成小红书舆情项目日报",
                List.of(),
                List.of(),
                List.of());
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            XhsDailyReport report = reportService.report(
                    request.argument("project_key"), request.argument("date"),
                    parseInt(request.argument("top_limit"), 5));
            return WechatReply.text(format(report));
        } catch (RuntimeException exception) {
            return WechatReply.text("小红书舆情日报生成失败：" + exception.getMessage());
        }
    }

    private String format(XhsDailyReport report) {
        StringBuilder text = new StringBuilder("小红书舆情日报 | ")
                .append(report.projectKey()).append(" | ").append(report.reportDate())
                .append("\n\n采集笔记：").append(report.collectedPosts()).append(" 条")
                .append("\n完成分析：").append(report.analyzedPosts()).append(" 条")
                .append("\n负面笔记：").append(report.negativePosts()).append(" 条")
                .append("\n高风险笔记：").append(report.highRiskPosts()).append(" 条")
                .append("\n平均风险分：").append(report.averageRiskScore())
                .append("\n新增事件：").append(report.newIncidents()).append(" 个")
                .append("\n当前未结事件：").append(report.activeIncidents()).append(" 个")
                .append("\n当日解决事件：").append(report.resolvedIncidents()).append(" 个");
        if (!report.categories().isEmpty()) {
            text.append("\n\n风险类别：");
            for (XhsRiskCategorySummary category : report.categories()) {
                text.append("\n- ").append(category.riskCategory())
                        .append("：").append(category.postCount()).append(" 条，均分 ")
                        .append(category.averageRiskScore()).append("，最高 ")
                        .append(category.maximumRiskScore());
            }
        }
        if (!report.topActiveIncidents().isEmpty()) {
            text.append("\n\n高风险未结事件：");
            for (XhsIncidentView incident : report.topActiveIncidents()) {
                text.append("\n- #").append(incident.incidentId()).append(" ")
                        .append(incident.riskLevel()).append(" ").append(incident.riskScore())
                        .append("分 | ").append(incident.status()).append(" | ").append(incident.title());
            }
        }
        return text.append("\n\n结果仅覆盖已采集并完成分析的数据。").toString();
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
