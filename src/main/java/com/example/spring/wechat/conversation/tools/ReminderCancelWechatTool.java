package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.reminder.model.ReminderException;
import com.example.spring.wechat.reminder.service.ReminderReplyFormatter;
import com.example.spring.wechat.reminder.service.ReminderService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReminderCancelWechatTool implements WechatTool {

    private final ReminderService reminderService;

    public ReminderCancelWechatTool(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Override
    public String name() {
        return "reminder_cancel";
    }

    @Override
    public String description() {
        return "按编号或标题取消待提醒任务。标题匹配多个任务时会返回候选，不会默认取消。";
    }

    @Override
    public List<String> arguments() {
        return List.of("reminder_id", "title");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.optionalString("reminder_id", "要取消的提醒编号；与 title 至少提供一个", "12"),
                WechatToolParameter.optionalString("title", "要取消的提醒标题或关键词", "喝水"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            return WechatReply.text(ReminderReplyFormatter.cancelled(reminderService.cancel(
                    new ReminderService.TargetCommand(
                            request.sessionKey(),
                            ReminderToolSupport.optionalTaskId(request),
                            request.argument("title")))));
        } catch (ReminderException exception) {
            return ReminderToolSupport.failure(exception);
        }
    }
}
