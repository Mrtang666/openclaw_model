package com.example.spring.wechat.login;

import com.example.spring.wechat.bot.WechatBotService;
import com.example.spring.wechat.bot.WechatBotState;
import com.example.spring.wechat.bot.multiclient.ClawBotConnectionSnapshot;
import com.example.spring.wechat.bot.multiclient.ClawBotManagerSnapshot;
import com.example.spring.wechat.bot.multiclient.ClawBotProcessingState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClawBotManagementControllerTests {

    @Test
    void returnsAggregateConnectionStateWithoutCaching() {
        WechatBotService service = mock(WechatBotService.class);
        ClawBotConnectionSnapshot connection = new ClawBotConnectionSnapshot(
                "connection-1", "用户 1", WechatBotState.RUNNING, ClawBotProcessingState.IDLE,
                "bot-1", "session-1", Instant.now(), Instant.now(), 0, 0, null);
        when(service.managerSnapshot()).thenReturn(new ClawBotManagerSnapshot(
                1, 0, 1, 10, 3, 8, 6, 0, 0, List.of(connection)));

        var response = new ClawBotManagementController(service).connections();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().connectedCount()).isEqualTo(1);
        assertThat(response.getBody().connections()).extracting(ClawBotConnectionSnapshot::connectionId)
                .containsExactly("connection-1");
    }

    @Test
    void reportsCapacityConflictWhenNewLoginCannotBeCreated() {
        WechatBotService service = mock(WechatBotService.class);
        when(service.addConnection()).thenThrow(new IllegalStateException("待扫码登录数已达到上限 3"));

        var response = new ClawBotManagementController(service).addConnection();

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().toString()).contains("上限 3");
    }

    @Test
    void returnsNotFoundWhenReconnectTargetDoesNotExist() {
        WechatBotService service = mock(WechatBotService.class);
        when(service.reconnectConnection("missing"))
                .thenThrow(new IllegalArgumentException("未找到微信连接：missing"));

        assertThat(new ClawBotManagementController(service).reconnect("missing").getStatusCode().value())
                .isEqualTo(404);
    }
}
