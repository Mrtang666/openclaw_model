package com.example.spring.wechat.reminder.service;

import com.example.spring.wechat.reminder.model.ReminderRepeatType;
import com.example.spring.wechat.reminder.model.ReminderStatus;
import com.example.spring.wechat.reminder.model.ReminderTask;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ReminderReplyFormatter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ReminderReplyFormatter() {
    }

    public static String created(ReminderTask task) {
        return "已创建提醒 #" + task.id() + "\n"
                + "时间：" + time(task.nextExecuteAt(), task.timezone()) + "\n"
                + "重复：" + repeat(task.repeatType()) + "\n"
                + "内容：" + task.title();
    }

    public static String listed(List<ReminderTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "你当前没有提醒任务。";
        }
        StringBuilder text = new StringBuilder("提醒任务：");
        for (ReminderTask task : tasks) {
            text.append("\n#").append(task.id())
                    .append(" [").append(status(task.status())).append("] ")
                    .append(task.title());
            if (task.nextExecuteAt() != null) {
                text.append("\n  时间：").append(time(task.nextExecuteAt(), task.timezone()));
            }
            text.append("，重复：").append(repeat(task.repeatType()));
        }
        return text.toString();
    }

    public static String cancelled(ReminderTask task) {
        return "已取消提醒 #" + task.id() + "：" + task.title();
    }

    public static String completed(ReminderTask task) {
        return "已标记完成提醒 #" + task.id() + "：" + task.title();
    }

    public static String snoozed(ReminderTask task) {
        String source = task.parentTaskId() == null ? "" : "（来源提醒 #" + task.parentTaskId() + "）";
        return "已安排提醒 #" + task.id() + source + "，时间："
                + time(task.nextExecuteAt(), task.timezone()) + "。";
    }

    public static String updated(ReminderTask task) {
        return "已修改提醒 #" + task.id() + "\n"
                + "时间：" + time(task.nextExecuteAt(), task.timezone()) + "\n"
                + "内容：" + task.title();
    }

    public static String notification(ReminderTask task) {
        StringBuilder text = new StringBuilder("提醒：").append(task.title());
        if (task.content() != null && !task.content().isBlank()
                && !task.content().strip().equals(task.title())) {
            text.append("\n").append(task.content().strip());
        }
        return text.toString();
    }

    private static String time(Instant instant, String timezone) {
        if (instant == null) {
            return "无";
        }
        return DATE_TIME.format(instant.atZone(ZoneId.of(timezone)));
    }

    private static String repeat(ReminderRepeatType value) {
        return switch (value) {
            case ONCE -> "仅一次";
            case DAILY -> "每天";
            case WEEKLY -> "每周";
        };
    }

    private static String status(ReminderStatus value) {
        return switch (value) {
            case ACTIVE -> "待提醒";
            case PROCESSING -> "发送中";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
            case FAILED -> "发送失败";
        };
    }
}
