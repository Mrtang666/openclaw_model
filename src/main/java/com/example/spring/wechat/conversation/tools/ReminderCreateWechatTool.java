package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.reminder.model.ReminderException;
import com.example.spring.wechat.reminder.model.ReminderTask;
import com.example.spring.wechat.reminder.service.ReminderReplyFormatter;
import com.example.spring.wechat.reminder.service.ReminderService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReminderCreateWechatTool implements WechatTool {

    private final ReminderService reminderService;

    public ReminderCreateWechatTool(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Override
    public String name() {
        return "reminder_create";
    }

    @Override
    public String description() {
        return "按用户明确指定的日期和钟点创建一次、每日或每周提醒。"
                + "几分钟后、几小时后等相对时间必须改用 reminder_create_after。";
    }

    @Override
    public List<String> arguments() {
        return List.of("title", "content", "execute_at", "repeat_type", "timezone");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("title", "提醒主题，例如：取快递", "取快递"),
                WechatToolParameter.requiredString(
                        "execute_at",
                        "用户明确指定的未来绝对时间，必须是 ISO-8601；相对时间禁止使用本工具",
                        "2026-07-27T19:30:00+08:00"),
                WechatToolParameter.optionalString("content", "补充提醒内容，可留空", "带上取件码"),
                WechatToolParameter.optionalEnum(
                        "repeat_type", "重复规则", List.of("once", "daily", "weekly"), "once"),
                WechatToolParameter.optionalString("timezone", "IANA 时区，默认 Asia/Shanghai", "Asia/Shanghai"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "保存提醒并在到期时主动发送微信文本消息",
                List.of(
                        "只能创建一次、每日或每周提醒，不支持任意 Cron、节假日规则或多渠道通知",
                        "时间含糊、缺少时间或时间已过去时必须追问或提示，不能自行猜测"),
                List.of("title", "明确且在未来的 execute_at；可选 content、repeat_type、timezone"),
                List.of("提醒编号、执行时间和重复规则"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            ReminderTask task = reminderService.create(new ReminderService.CreateCommand(
                    request.sessionKey(),
                    request.argument("title"),
                    request.argument("content"),
                    request.argument("execute_at"),
                    request.argument("repeat_type"),
                    request.argument("timezone")));
            return WechatReply.text(ReminderReplyFormatter.created(task));
        } catch (ReminderException exception) {
            return ReminderToolSupport.failure(exception);
        }
    }
}
