package com.example.spring.command;

import com.example.spring.wechat.WechatBotService;
import com.example.spring.wechat.WechatBotState;
import com.example.spring.wechat.WechatBotStatus;
import com.example.spring.wechat.WechatStartResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WechatCommandTests {

    @Test
    void startsWechatBotAndPrintsQrCodeContent() {
        WechatBotService service = mock(WechatBotService.class);
        when(service.start()).thenReturn(new WechatStartResult(true, "请扫码登录", "QR-CONTENT"));
        WechatCommand command = new WechatCommand(service);

        assertThat(command.execute(List.of("start")))
                .contains("请扫码登录")
                .contains("QR-CONTENT")
                .contains("wechat status");
    }

    @Test
    void printsStatus() {
        WechatBotService service = mock(WechatBotService.class);
        when(service.status()).thenReturn(new WechatBotStatus(
                WechatBotState.RUNNING, "bot-1", null));
        WechatCommand command = new WechatCommand(service);

        assertThat(command.execute(List.of("status")))
                .contains("状态：RUNNING")
                .contains("botId：bot-1");
    }

    @Test
    void stopsWechatBot() {
        WechatBotService service = mock(WechatBotService.class);
        when(service.stop()).thenReturn("微信 Bot 已停止");
        WechatCommand command = new WechatCommand(service);

        assertThat(command.execute(List.of("stop"))).isEqualTo("微信 Bot 已停止");
    }
}
