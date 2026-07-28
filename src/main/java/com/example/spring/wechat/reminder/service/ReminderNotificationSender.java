package com.example.spring.wechat.reminder.service;

public interface ReminderNotificationSender {

    void sendText(String connectionId, String recipientId, String text);
}
