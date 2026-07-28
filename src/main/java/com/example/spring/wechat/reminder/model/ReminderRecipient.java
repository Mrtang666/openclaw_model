package com.example.spring.wechat.reminder.model;

public record ReminderRecipient(String connectionId, String userId) {

    private static final String SESSION_PREFIX = "clawbot:";

    public ReminderRecipient {
        connectionId = clean(connectionId);
        userId = clean(userId);
    }

    public static ReminderRecipient fromSessionKey(String sessionKey) {
        String value = clean(sessionKey);
        if (!value.startsWith(SESSION_PREFIX)) {
            throw new ReminderException("当前会话不支持主动微信提醒，请通过已登录的微信 Bot 创建提醒");
        }

        String[] parts = value.substring(SESSION_PREFIX.length()).split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ReminderException("当前会话缺少微信连接信息，暂时无法创建主动提醒");
        }
        return new ReminderRecipient(parts[0], parts[1]);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
