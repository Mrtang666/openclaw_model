package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.reminder.model.ReminderException;

final class ReminderToolSupport {

    private ReminderToolSupport() {
    }

    static long taskId(WechatToolRequest request) {
        String value = request == null ? "" : request.argument("reminder_id");
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new ReminderException("reminder_id 必须是有效的提醒编号");
        }
    }

    static Long optionalTaskId(WechatToolRequest request) {
        String value = request == null ? "" : request.argument("reminder_id");
        if (value.isBlank()) {
            return null;
        }
        return taskId(request);
    }

    static Long delayValue(WechatToolRequest request) {
        String value = request == null ? "" : request.argument("delay_value");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new ReminderException("delay_value 必须是有效的正整数");
        }
    }

    static Integer optionalLimit(WechatToolRequest request) {
        String value = request == null ? "" : request.argument("limit");
        if (value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new ReminderException("limit 必须是有效整数");
        }
    }

    static WechatReply failure(ReminderException exception) {
        return WechatReply.text("提醒操作未完成：" + exception.getMessage());
    }
}
