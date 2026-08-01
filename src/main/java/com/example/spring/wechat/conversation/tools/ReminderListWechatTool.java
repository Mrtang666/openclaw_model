package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.reminder.model.ReminderException;
import com.example.spring.wechat.reminder.service.ReminderReplyFormatter;
import com.example.spring.wechat.reminder.service.ReminderService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReminderListWechatTool implements WechatTool {

    private final ReminderService reminderService;

    public ReminderListWechatTool(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Override
    public String name() {
        return "reminder_list";
    }

    @Override
    public String description() {
        return "按状态和关键词查询当前微信会话的提醒，返回编号、状态、时间和重复规则。";
    }

    @Override
    public List<String> arguments() {
        return List.of("status", "keyword", "limit");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.optionalEnum(
                        "status", "状态过滤", List.of("all", "active", "processing", "completed", "cancelled", "failed"), "active"),
                WechatToolParameter.optionalString("keyword", "标题或内容关键词", "喝水"),
                new WechatToolParameter("limit", "integer", false, "返回数量，1 到 100，默认 20", List.of(), "20"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            return WechatReply.text(ReminderReplyFormatter.listed(reminderService.list(
                    new ReminderService.ListCommand(
                            request.sessionKey(),
                            request.argument("status"),
                            request.argument("keyword"),
                            ReminderToolSupport.optionalLimit(request)))));
        } catch (ReminderException exception) {
            return ReminderToolSupport.failure(exception);
        }
    }
}
