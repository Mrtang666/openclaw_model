package com.example.spring.wechat.conversation.tools;

import com.example.spring.xhs.alert.XhsAlertService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XhsAlertSubscribeWechatToolTests {

    @Test
    void bindsSubscriptionToCurrentConnectionAndUser() {
        XhsAlertService alertService = mock(XhsAlertService.class);
        when(alertService.subscribeWechat("brand-a", "connection-1", "user-1", 70, 30)).thenReturn(12L);
        XhsAlertSubscribeWechatTool tool = new XhsAlertSubscribeWechatTool(alertService);
        WechatToolRequest request = new WechatToolRequest(
                "connection-1:user-1", "订阅品牌告警",
                Map.of("project_key", "brand-a", "minimum_risk_score", "70", "cooldown_minutes", "30"),
                "", List.of(), null, null);

        var reply = tool.execute(request);

        verify(alertService).subscribeWechat("brand-a", "connection-1", "user-1", 70, 30);
        assertThat(reply.text()).contains("subscription_id=12", "最低风险分=70");
    }

    @Test
    void rejectsSessionWithoutConnectionId() {
        XhsAlertService alertService = mock(XhsAlertService.class);
        XhsAlertSubscribeWechatTool tool = new XhsAlertSubscribeWechatTool(alertService);

        var reply = tool.execute(new WechatToolRequest(
                "user-1", "订阅", Map.of("project_key", "brand-a"), "", List.of(), null, null));

        assertThat(reply.text()).contains("无法确定微信连接");
    }

    @Test
    void acknowledgesOnlyThroughCurrentConnectionAndUser() {
        XhsAlertService alertService = mock(XhsAlertService.class);
        when(alertService.acknowledge("brand-a", 42L, "connection-1", "user-1")).thenReturn(true);
        XhsAlertAcknowledgeWechatTool tool = new XhsAlertAcknowledgeWechatTool(alertService);
        WechatToolRequest request = new WechatToolRequest(
                "connection-1:user-1", "确认告警",
                Map.of("project_key", "brand-a", "alert_event_id", "42"),
                "", List.of(), null, null);

        var reply = tool.execute(request);

        verify(alertService).acknowledge("brand-a", 42L, "connection-1", "user-1");
        assertThat(reply.text()).contains("告警已确认", "alert_event_id=42");
    }

    @Test
    void rejectsAcknowledgementWithoutConnectionId() {
        XhsAlertAcknowledgeWechatTool tool = new XhsAlertAcknowledgeWechatTool(mock(XhsAlertService.class));

        var reply = tool.execute(new WechatToolRequest(
                "user-1", "确认告警", Map.of("project_key", "brand-a", "alert_event_id", "42"),
                "", List.of(), null, null));

        assertThat(reply.text()).contains("无法确定微信连接");
    }
}
