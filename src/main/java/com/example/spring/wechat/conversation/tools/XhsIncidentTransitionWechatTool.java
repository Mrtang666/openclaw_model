package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.xhs.incident.XhsIncidentTransition;
import com.example.spring.xhs.incident.XhsIncidentWorkflowService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "xhs.alert", name = "enabled", havingValue = "true")
public class XhsIncidentTransitionWechatTool implements WechatTool {

    private final XhsIncidentWorkflowService workflowService;

    public XhsIncidentTransitionWechatTool(XhsIncidentWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public String name() {
        return "xhs_incident_transition";
    }

    @Override
    public String description() {
        return "更新小红书舆情事件的处置状态，并记录当前微信连接、用户和处置说明。";
    }

    @Override
    public List<String> arguments() {
        return List.of("project_key", "incident_id", "target_status", "note");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("project_key", "舆情项目唯一标识", "brand-a"),
                WechatToolParameter.requiredString("incident_id", "事件编号", "123"),
                new WechatToolParameter(
                        "target_status", "string", true, "目标处置状态",
                        List.of("ACKNOWLEDGED", "INVESTIGATING", "RESOLVED"), "INVESTIGATING"),
                WechatToolParameter.optionalString("note", "处置说明，最多 1000 字", "已安排客服核查相关批次"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "按严格状态机推进舆情事件并保留操作审计。",
                List.of("只有当前连接和用户的有效项目告警订阅者可以操作；不能跳过调查直接解决事件。"),
                List.of("需要项目、事件编号和目标状态。"),
                List.of("返回原状态、目标状态以及是否实际发生变更。"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String connectionId = connectionId(request.sessionKey());
        if (connectionId.isBlank() || request.userId().isBlank()) {
            return WechatReply.text("当前会话无法确定微信连接，不能处置舆情事件。");
        }
        try {
            long incidentId = Long.parseLong(request.argument("incident_id"));
            XhsIncidentTransition result = workflowService.transition(
                    request.argument("project_key"), incidentId, request.argument("target_status"),
                    connectionId, request.userId(), request.argument("note"));
            if (!result.changed()) {
                return WechatReply.text("舆情事件状态未变化：incident_id=" + incidentId
                        + "，当前状态=" + result.toStatus() + "。");
            }
            return WechatReply.text("舆情事件状态已更新：incident_id=" + incidentId
                    + "，" + result.fromStatus() + " -> " + result.toStatus() + "。操作已记录审计日志。");
        } catch (NumberFormatException exception) {
            return WechatReply.text("incident_id 必须是数字。");
        } catch (RuntimeException exception) {
            return WechatReply.text("小红书舆情事件处置失败：" + exception.getMessage());
        }
    }

    private String connectionId(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return "";
        }
        int separator = sessionKey.lastIndexOf(':');
        return separator <= 0 ? "" : sessionKey.substring(0, separator).strip();
    }
}
