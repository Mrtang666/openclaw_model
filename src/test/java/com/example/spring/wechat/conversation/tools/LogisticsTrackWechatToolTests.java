package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.commerce.logistics.model.Carrier;
import com.example.spring.wechat.commerce.logistics.model.ShipmentEvent;
import com.example.spring.wechat.commerce.logistics.model.ShipmentStatus;
import com.example.spring.wechat.commerce.logistics.model.ShipmentTrace;
import com.example.spring.wechat.commerce.logistics.service.LogisticsTrackService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogisticsTrackWechatToolTests {

    @Test
    void exposesClearFunctionCallingDefinition() {
        LogisticsTrackWechatTool tool = new LogisticsTrackWechatTool(mock(LogisticsTrackService.class));

        assertThat(tool.name()).isEqualTo("logistics_track");
        assertThat(tool.arguments()).containsExactly("tracking_no", "carrier", "phone_last4");
        assertThat(tool.parameters())
                .filteredOn(parameter -> parameter.name().equals("tracking_no"))
                .singleElement()
                .satisfies(parameter -> assertThat(parameter.required()).isTrue());
        assertThat(tool.capability().toPromptText())
                .contains("取件码")
                .contains("物流订阅");
    }

    @Test
    void formatsTraceAndForwardsArguments() {
        LogisticsTrackService service = mock(LogisticsTrackService.class);
        LogisticsTrackWechatTool tool = new LogisticsTrackWechatTool(service);
        when(service.track("SF1234567890", "sf", "5678")).thenReturn(trace());

        WechatReply reply = tool.execute(request(Map.of(
                "tracking_no", "SF1234567890",
                "carrier", "sf",
                "phone_last4", "5678")));

        verify(service).track("SF1234567890", "sf", "5678");
        assertThat(reply.text())
                .contains("SF1****7890")
                .contains("派送中")
                .contains("正在派送")
                .contains("2026-07-23");
    }

    @Test
    void asksForTrackingNumberWhenMissing() {
        LogisticsTrackWechatTool tool = new LogisticsTrackWechatTool(mock(LogisticsTrackService.class));

        WechatReply reply = tool.execute(request(Map.of()));

        assertThat(reply.text()).contains("快递单号");
    }

    private static ShipmentTrace trace() {
        return new ShipmentTrace(
                "SF1****7890",
                Carrier.SF,
                ShipmentStatus.OUT_FOR_DELIVERY,
                "杭州市西湖区",
                "今天 18:00 前",
                List.of(new ShipmentEvent(
                        Instant.parse("2026-07-23T03:00:00Z"),
                        "杭州市西湖区",
                        "快件正在派送")),
                Instant.parse("2026-07-23T04:00:00Z"));
    }

    private static WechatToolRequest request(Map<String, String> arguments) {
        return new WechatToolRequest("user-1", "", arguments, "", null, null);
    }
}
