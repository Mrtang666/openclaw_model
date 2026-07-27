package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.food.model.FoodDeliveryAddress;
import com.example.spring.wechat.food.model.FoodPaymentHandoff;
import com.example.spring.wechat.food.service.FoodOrderOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FoodDeliveryWechatToolTests {

    private final FoodOrderOrchestrationService service = mock(FoodOrderOrchestrationService.class);
    private final FoodDeliveryWechatTool tool = new FoodDeliveryWechatTool(service, new ObjectMapper());

    @Test
    void exposesAddressReusePrompt() {
        when(service.addresses("user-1")).thenReturn(List.of(new FoodDeliveryAddress(
                "address-1", "user-1", "家", "张先生", "13800138000",
                "杭州", "滨江区", "星光大道3幢1202", "", "", true, Instant.now())));

        String reply = tool.execute(request("查看地址", Map.of("operation", "list_addresses"))).text();

        assertThat(reply).contains("上次使用的是").contains("是否仍送到这里").doesNotContain("13800138000");
    }

    @Test
    void paymentShowsMiniProgramFallback() {
        when(service.createPayment("user-1", "order-1")).thenReturn(new FoodPaymentHandoff(
                "handoff-1", "order-1", FoodPaymentHandoff.Type.WECHAT_JSAPI,
                "https://pay.example.com/order-1", "weixin://dl/business/?t=food-order-1",
                Instant.now().plusSeconds(600), "CREATED"));

        String reply = tool.execute(request("支付订单", Map.of(
                "operation", "create_payment", "order_id", "order-1"))).text();

        assertThat(reply).contains("打开支付页面").contains("小程序/H5兜底").contains("weixin://");
    }

    @Test
    void toolDescriptionRequiresExplicitConfirmation() {
        assertThat(tool.capability().toPromptText())
                .contains("明确回复确认下单")
                .contains("支付密码");
    }

    private WechatToolRequest request(String userText, Map<String, String> arguments) {
        return new WechatToolRequest(
                "clawbot:connection-1:user-1", userText, arguments, "", null, null);
    }
}
