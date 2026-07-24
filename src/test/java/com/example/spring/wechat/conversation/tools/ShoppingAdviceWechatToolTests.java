package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.commerce.advice.service.ShoppingAdviceService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingAdviceWechatToolTests {

    @Test
    void exposesAdviceOnlyCapability() {
        ShoppingAdviceWechatTool tool = new ShoppingAdviceWechatTool(new ShoppingAdviceService());

        assertThat(tool.name()).isEqualTo("shopping_advice");
        assertThat(tool.arguments()).containsExactly(
                "product", "budget_min", "budget_max", "usage", "preferences", "constraints");
        assertThat(tool.parameters())
                .filteredOn(parameter -> parameter.name().equals("product"))
                .singleElement()
                .satisfies(parameter -> assertThat(parameter.required()).isTrue());
        assertThat(tool.capability().toPromptText())
                .contains("不搜索")
                .contains("具体商品链接")
                .contains("map_search");
    }

    @Test
    void returnsStructuredAdviceWithoutProductLinks() {
        ShoppingAdviceWechatTool tool = new ShoppingAdviceWechatTool(new ShoppingAdviceService());

        WechatReply reply = tool.execute(request(Map.of(
                "product", "蓝牙耳机",
                "budget_min", "100",
                "budget_max", "300",
                "usage", "通勤",
                "preferences", "降噪和续航",
                "constraints", "不要入耳式")));

        assertThat(reply.text())
                .contains("蓝牙耳机选购建议")
                .contains("优先关注")
                .contains("常见误区")
                .contains("下单前检查")
                .doesNotContain("http://", "https://");
    }

    @Test
    void returnsReadableMessageForInvalidBudget() {
        ShoppingAdviceWechatTool tool = new ShoppingAdviceWechatTool(new ShoppingAdviceService());

        WechatReply reply = tool.execute(request(Map.of(
                "product", "蓝牙耳机",
                "budget_max", "invalid")));

        assertThat(reply.text()).contains("budget_max");
    }

    private static WechatToolRequest request(Map<String, String> arguments) {
        return new WechatToolRequest("user-1", "", arguments, "", null, null);
    }
}
