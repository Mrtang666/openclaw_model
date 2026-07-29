package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.xhs.alert.XhsAlertService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "xhs.alert", name = "enabled", havingValue = "true")
public class XhsAlertSubscribeWechatTool implements WechatTool {

    private final XhsAlertService alertService;

    public XhsAlertSubscribeWechatTool(XhsAlertService alertService) {
        this.alertService = alertService;
    }

    @Override
    public String name() {
        return "xhs_alert_subscribe";
    }

    @Override
    public String description() {
        return "为当前微信用户订阅指定小红书舆情项目的风险告警。";
    }

    @Override
    public List<String> arguments() {
        return List.of("project_key", "minimum_risk_score", "cooldown_minutes");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("project_key", "舆情项目唯一标识", "brand-a"),
                WechatToolParameter.optionalString("minimum_risk_score", "最低告警风险分，默认 60", "60"),
                WechatToolParameter.optionalString("cooldown_minutes", "冷却时间，默认 60 分钟", "60"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "把当前微信会话绑定为舆情告警接收方。",
                List.of("只能为当前微信用户订阅，不能指定其他接收人。"),
                List.of("需要 project_key；当前会话必须包含有效微信连接 ID。"),
                List.of("返回订阅 ID、阈值和接收用户。"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String connectionId = connectionId(request.sessionKey());
        if (connectionId.isBlank() || request.userId().isBlank()) {
            return WechatReply.text("当前会话无法确定微信连接，不能创建主动告警订阅。");
        }
        try {
            int threshold = parseInt(request.argument("minimum_risk_score"), 60);
            int cooldown = parseInt(request.argument("cooldown_minutes"), 60);
            long subscriptionId = alertService.subscribeWechat(
                    request.argument("project_key"), connectionId, request.userId(), threshold, cooldown);
            return WechatReply.text("小红书舆情告警已订阅：subscription_id=" + subscriptionId
                    + "，最低风险分=" + Math.max(0, Math.min(100, threshold)) + "。只有新产生的告警会主动发送。");
        } catch (RuntimeException exception) {
            return WechatReply.text("小红书舆情告警订阅失败：" + exception.getMessage());
        }
    }

    private String connectionId(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return "";
        }
        int separator = sessionKey.lastIndexOf(':');
        return separator <= 0 ? "" : sessionKey.substring(0, separator).strip();
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
