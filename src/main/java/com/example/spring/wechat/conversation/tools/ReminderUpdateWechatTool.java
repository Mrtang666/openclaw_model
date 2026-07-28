package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.reminder.model.ReminderException;
import com.example.spring.wechat.reminder.service.ReminderReplyFormatter;
import com.example.spring.wechat.reminder.service.ReminderService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReminderUpdateWechatTool implements WechatTool {

    private final ReminderService reminderService;

    public ReminderUpdateWechatTool(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Override
    public String name() {
        return "reminder_update";
    }

    @Override
    public String description() {
        return "按编号或当前标题修改待提醒任务的标题、内容或时间。相对时间使用 delay_value 和 delay_unit。";
    }

    @Override
    public List<String> arguments() {
        return List.of(
                "reminder_id", "current_title", "new_title", "content", "clear_content",
                "execute_at", "delay_value", "delay_unit", "timezone");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.optionalString("reminder_id", "要修改的提醒编号；与 current_title 至少提供一个", "12"),
                WechatToolParameter.optionalString("current_title", "当前提醒标题或关键词", "交水费"),
                WechatToolParameter.optionalString("new_title", "修改后的标题", "交电费"),
                WechatToolParameter.optionalString("content", "修改后的补充内容", "使用支付宝缴费"),
                WechatToolParameter.optionalBoolean("clear_content", "是否清空补充内容", false),
                WechatToolParameter.optionalString("execute_at", "新的明确 ISO-8601 时间；不能和相对延迟同时使用", "2026-07-28T20:00:00+08:00"),
                new WechatToolParameter("delay_value", "integer", false, "从程序执行时刻起延后的数值", List.of(), "2"),
                WechatToolParameter.optionalEnum(
                        "delay_unit", "相对延迟单位，不要换算", List.of("minutes", "hours", "days"), "hours"),
                WechatToolParameter.optionalString("timezone", "IANA 时区，默认保留原时区", "Asia/Shanghai"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            Long delayValue = request.argument("delay_value").isBlank()
                    ? null
                    : ReminderToolSupport.delayValue(request);
            return WechatReply.text(ReminderReplyFormatter.updated(reminderService.update(
                    new ReminderService.UpdateCommand(
                            request.sessionKey(),
                            ReminderToolSupport.optionalTaskId(request),
                            request.argument("current_title"),
                            request.argument("new_title"),
                            request.argument("content"),
                            request.booleanArgument("clear_content"),
                            request.argument("execute_at"),
                            delayValue,
                            request.argument("delay_unit"),
                            request.argument("timezone")))));
        } catch (ReminderException exception) {
            return ReminderToolSupport.failure(exception);
        }
    }
}
