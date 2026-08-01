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
    void exposesAddressReusePromptWithoutRawPhone() {
        when(service.addresses("user-1")).thenReturn(List.of(new FoodDeliveryAddress(
                "address-1", "user-1", "Home", "Zhang", "13800138000",
                "Hangzhou", "Binjiang", "Xingguang Road Building 3 Room 202", "", "", true, Instant.now())));

        String reply = tool.execute(request("show address", Map.of("operation", "list_addresses"))).text();

        assertThat(reply).contains("address-1").doesNotContain("13800138000");
    }

    @Test
    void paymentShowsReturnedPaymentLinks() {
        when(service.createPayment("user-1", "order-1")).thenReturn(new FoodPaymentHandoff(
                "handoff-1", "order-1", FoodPaymentHandoff.Type.WECHAT_JSAPI,
                "https://pay.example.com/order-1", "weixin://dl/business/?t=food-order-1",
                Instant.now().plusSeconds(600), "CREATED"));

        String reply = tool.execute(request("pay order", Map.of(
                "operation", "create_payment", "order_id", "order-1"))).text();

        assertThat(reply).contains("https://pay.example.com/order-1", "weixin://");
    }

    @Test
    void exposesCompactMetadataBecauseWorkflowRulesLiveInSkill() {
        assertThat(tool.name()).isEqualTo("food_delivery");
        assertThat(tool.description().length()).isLessThanOrEqualTo(80);
        assertThat(tool.capability().summary()).isNotBlank();
        assertThat(tool.capability().boundaries()).isEmpty();
        assertThat(tool.capability().requiredInformation()).isEmpty();
        assertThat(tool.capability().outputs()).isEmpty();
    }

    private WechatToolRequest request(String userText, Map<String, String> arguments) {
        return new WechatToolRequest(
                "clawbot:connection-1:user-1", userText, arguments, "", null, null);
    }
}
