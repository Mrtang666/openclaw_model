package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.xhs.analysis.XhsOpinionQueryService;
import com.example.spring.xhs.analysis.XhsOpinionView;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class XhsOpinionSearchWechatTool implements WechatTool {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final XhsOpinionQueryService queryService;

    public XhsOpinionSearchWechatTool(XhsOpinionQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public String name() {
        return "xhs_opinion_search";
    }

    @Override
    public String description() {
        return "查询已采集并分析的小红书舆情，返回风险、摘要、采集范围内的证据链接和分析时间。";
    }

    @Override
    public List<String> arguments() {
        return List.of("project_key", "keyword", "sentiment", "minimum_risk_score", "limit");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("project_key", "舆情项目唯一标识", "brand-a"),
                WechatToolParameter.optionalString("keyword", "标题、正文或摘要关键词", "过敏"),
                WechatToolParameter.optionalEnum("sentiment", "情感筛选", List.of("POSITIVE", "NEUTRAL", "NEGATIVE"), "NEGATIVE"),
                WechatToolParameter.optionalString("minimum_risk_score", "最低风险分，0-100", "40"),
                WechatToolParameter.optionalString("limit", "结果数量，默认 10，最多 50", "10"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "检索舆情分析结果并返回可追溯证据。",
                List.of("只查询已采集数据；不能把没有结果解释为平台上不存在相关内容。"),
                List.of("需要 project_key；其他筛选条件可选。"),
                List.of("风险等级、风险分、摘要、发布时间、分析时间和来源链接。"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            List<XhsOpinionView> results = queryService.search(
                    request.argument("project_key"), request.argument("keyword"), request.argument("sentiment"),
                    parseInt(request.argument("minimum_risk_score"), 0), parseInt(request.argument("limit"), 10));
            if (results.isEmpty()) {
                return WechatReply.text("当前采集范围内没有符合条件的已分析小红书舆情。请检查项目标识、筛选条件和采集状态。");
            }
            StringBuilder text = new StringBuilder("小红书舆情查询结果（").append(results.size()).append(" 条）：");
            for (int index = 0; index < results.size(); index++) {
                XhsOpinionView result = results.get(index);
                text.append("\n\n[").append(index + 1).append("] ")
                        .append(result.riskLevel()).append(" ").append(result.riskScore()).append("分")
                        .append(" | ").append(result.sentiment()).append(" | ").append(result.riskCategory())
                        .append("\n").append(defaultText(result.title(), "无标题"))
                        .append("\n摘要：").append(defaultText(result.summary(), "无摘要"));
                if (result.publishedAt() != null) {
                    text.append("\n发布时间：").append(TIME.format(result.publishedAt()));
                }
                text.append("\n分析时间：").append(TIME.format(result.analyzedAt()));
                if (result.sourceUrl() != null && !result.sourceUrl().isBlank()) {
                    text.append("\n来源：").append(result.sourceUrl());
                }
            }
            return WechatReply.text(text.toString());
        } catch (RuntimeException exception) {
            return WechatReply.text("小红书舆情查询失败：" + defaultText(exception.getMessage(), "未知错误"));
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
