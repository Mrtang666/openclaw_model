package com.example.spring.xhs.alert;

import com.example.spring.wechat.bot.WechatBotService;
import org.springframework.stereotype.Component;

@Component
public class WechatXhsAlertNotifier implements XhsAlertNotifier {

    private final WechatBotService wechatBotService;

    public WechatXhsAlertNotifier(WechatBotService wechatBotService) {
        this.wechatBotService = wechatBotService;
    }

    @Override
    public void send(XhsAlertDelivery delivery, String message) {
        if (!wechatBotService.sendProactiveText(delivery.connectionId(), delivery.recipientId(), message)) {
            throw new IllegalStateException("目标微信连接不可用");
        }
    }
}
