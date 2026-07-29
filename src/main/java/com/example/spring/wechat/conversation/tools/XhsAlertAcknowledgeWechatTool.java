package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.xhs.alert.XhsAlertService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "xhs.alert", name = "enabled", havingValue = "true")
public class XhsAlertAcknowledgeWechatTool implements WechatTool {

    private final XhsAlertService alertService;

    public XhsAlertAcknowledgeWechatTool(XhsAlertService alertService) {
        this.alertService = alertService;
    }

    @Override
    public String name() {
        return "xhs_alert_acknowledge";
    }

    @Override
    public String description() {
        return "确认当前微信用户收到的小红书舆情告警。";
    }

    @Override
    public List<String> arguments() {
        return List.of("project_key", "alert_event_id");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("project_key", "舆情项目唯一标识", "brand-a"),
                WechatToolParameter.requiredString("alert_event_id", "告警编号", "123"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "确认告警并记录确认时间。",
                List.of("只有该项目的已订阅接收人可以确认，不能确认其他用户的告警。"),
                List.of("需要 project_key 和 alert_event_id。"),
                List.of("返回确认是否成功。"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String connectionId = connectionId(request.sessionKey());
        if (connectionId.isBlank() || request.userId().isBlank()) {
            return WechatReply.text("当前会话无法确定微信连接，不能确认主动告警。");
        }
        try {
            long alertEventId = Long.parseLong(request.argument("alert_event_id"));
            boolean acknowledged = alertService.acknowledge(
                    request.argument("project_key"), alertEventId, connectionId, request.userId());
            return WechatReply.text(acknowledged
                    ? "小红书舆情告警已确认：alert_event_id=" + alertEventId
                    : "未找到可由当前用户确认的告警，请检查项目和告警编号。");
        } catch (NumberFormatException exception) {
            return WechatReply.text("alert_event_id 必须是数字。");
        } catch (RuntimeException exception) {
            return WechatReply.text("小红书舆情告警确认失败：" + exception.getMessage());
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
