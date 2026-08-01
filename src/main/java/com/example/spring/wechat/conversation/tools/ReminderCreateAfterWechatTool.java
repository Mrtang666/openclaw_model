package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.reminder.model.ReminderException;
import com.example.spring.wechat.reminder.model.ReminderTask;
import com.example.spring.wechat.reminder.service.ReminderReplyFormatter;
import com.example.spring.wechat.reminder.service.ReminderService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReminderCreateAfterWechatTool implements WechatTool {

    private final ReminderService reminderService;

    public ReminderCreateAfterWechatTool(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Override
    public String name() {
        return "reminder_create_after";
    }

    @Override
    public String description() {
        return "创建相对时间提醒。用户说几分钟后、几小时后或几天后时必须调用本工具，"
                + "原样提取 delay_value 和 delay_unit，禁止换算或猜测 execute_at。";
    }

    @Override
    public List<String> arguments() {
        return List.of("title", "content", "delay_value", "delay_unit", "timezone");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("title", "提醒主题，例如：喝水", "喝水"),
                new WechatToolParameter(
                        "delay_value",
                        "integer",
                        true,
                        "从程序执行时刻开始延后的数值，只填写用户明确说出的数值",
                        List.of(),
                        "2"),
                new WechatToolParameter(
                        "delay_unit",
                        "string",
                        true,
                        "用户明确说出的延迟单位，不要换算",
                        List.of("minutes", "hours", "days"),
                        "minutes"),
                WechatToolParameter.optionalString("content", "补充提醒内容，可留空", "去喝一杯水"),
                WechatToolParameter.optionalString("timezone", "IANA 时区，默认 Asia/Shanghai", "Asia/Shanghai"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "由 Java 根据服务器当前时间创建几分钟后的微信提醒",
                List.of(
                        "只处理明确的相对时间，不接受绝对时间",
                        "不得自行获取或计算当前时间，不得把相对时间转换为 execute_at",
                        "暂不支持每隔若干分钟循环提醒"),
                List.of("title、用户明确给出的 delay_value 和 delay_unit"),
                List.of("提醒编号及由程序计算出的真实执行时间"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            ReminderTask task = reminderService.createAfter(new ReminderService.CreateAfterCommand(
                    request.sessionKey(),
                    request.argument("title"),
                    request.argument("content"),
                    ReminderToolSupport.delayValue(request),
                    request.argument("delay_unit"),
                    request.argument("timezone")));
            return WechatReply.text(ReminderReplyFormatter.created(task));
        } catch (ReminderException exception) {
            return ReminderToolSupport.failure(exception);
        }
    }
}
