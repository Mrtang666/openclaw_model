package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.xhs.analysis.XhsIncidentView;
import com.example.spring.xhs.analysis.XhsOpinionQueryService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class XhsIncidentListWechatTool implements WechatTool {

    private final XhsOpinionQueryService queryService;

    public XhsIncidentListWechatTool(XhsOpinionQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public String name() {
        return "xhs_incident_list";
    }

    @Override
    public String description() {
        return "查看小红书舆情项目中聚合后的高风险事件。";
    }

    @Override
    public List<String> arguments() {
        return List.of("project_key", "status", "limit");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("project_key", "舆情项目唯一标识", "brand-a"),
                WechatToolParameter.optionalEnum("status", "事件状态", List.of("OPEN", "ACKNOWLEDGED", "INVESTIGATING", "RESOLVED"), "OPEN"),
                WechatToolParameter.optionalString("limit", "结果数量，默认 10", "10"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "查询小红书舆情事件列表",
                List.of(),
                List.of(),
                List.of());
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            List<XhsIncidentView> incidents = queryService.incidents(
                    request.argument("project_key"), request.argument("status"), parseInt(request.argument("limit"), 10));
            if (incidents.isEmpty()) {
                return WechatReply.text("当前采集范围内没有符合条件的舆情事件。");
            }
            StringBuilder text = new StringBuilder("小红书舆情事件（").append(incidents.size()).append(" 个）：");
            for (int index = 0; index < incidents.size(); index++) {
                XhsIncidentView incident = incidents.get(index);
                text.append("\n\n[").append(index + 1).append("] ")
                        .append(incident.riskLevel()).append(" ").append(incident.riskScore()).append("分")
                        .append(" | ").append(incident.status()).append(" | ").append(incident.riskCategory())
                        .append("\n事件编号：").append(incident.incidentId())
                        .append("\n").append(incident.title())
                        .append("\n关联笔记：").append(incident.postCount()).append(" 条");
            }
            return WechatReply.text(text.toString());
        } catch (RuntimeException exception) {
            return WechatReply.text("小红书舆情事件查询失败：" + exception.getMessage());
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
