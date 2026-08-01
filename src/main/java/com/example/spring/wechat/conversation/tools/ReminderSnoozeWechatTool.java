package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.reminder.model.ReminderException;
import com.example.spring.wechat.reminder.service.ReminderReplyFormatter;
import com.example.spring.wechat.reminder.service.ReminderService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReminderSnoozeWechatTool implements WechatTool {

    private final ReminderService reminderService;

    public ReminderSnoozeWechatTool(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Override
    public String name() {
        return "reminder_snooze";
    }

    @Override
    public String description() {
        return "延后指定提醒；未提供编号或标题时，对当前会话最近发送的提醒创建一次后续提醒。";
    }

    @Override
    public List<String> arguments() {
        return List.of("reminder_id", "title", "delay_value", "delay_unit");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.optionalString("reminder_id", "要延后的提醒编号；再提醒当前消息时可留空", "12"),
                WechatToolParameter.optionalString("title", "要延后的提醒标题；再提醒当前消息时可留空", "喝水"),
                new WechatToolParameter("delay_value", "integer", true, "延后的数值", List.of(), "10"),
                new WechatToolParameter(
                        "delay_unit", "string", true, "延迟单位，不要换算",
                        List.of("minutes", "hours", "days"), "minutes"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            return WechatReply.text(ReminderReplyFormatter.snoozed(reminderService.snooze(
                    new ReminderService.SnoozeCommand(
                            request.sessionKey(),
                            ReminderToolSupport.optionalTaskId(request),
                            request.argument("title"),
                            ReminderToolSupport.delayValue(request),
                            request.argument("delay_unit")))));
        } catch (ReminderException exception) {
            return ReminderToolSupport.failure(exception);
        }
    }
}
