package com.example.spring.wechat.reminder.service;

import com.example.spring.wechat.bot.WechatBotService;
import org.springframework.stereotype.Service;

@Service
public class WechatReminderNotificationSender implements ReminderNotificationSender {

    private final WechatBotService wechatBotService;

    public WechatReminderNotificationSender(WechatBotService wechatBotService) {
        this.wechatBotService = wechatBotService;
    }

    @Override
    public void sendText(String connectionId, String recipientId, String text) {
        if (!wechatBotService.sendProactiveText(connectionId, recipientId, text)) {
            throw new IllegalStateException("提醒对应的微信连接当前不可用，请在 Bot 登录后重试");
        }
    }
}
