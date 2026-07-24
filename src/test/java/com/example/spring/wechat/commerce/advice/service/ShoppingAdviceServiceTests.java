package com.example.spring.wechat.commerce.advice.service;

import com.example.spring.wechat.commerce.advice.model.ShoppingAdviceException;
import com.example.spring.wechat.commerce.advice.model.ShoppingAdviceRequest;
import com.example.spring.wechat.commerce.advice.model.ShoppingAdviceResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShoppingAdviceServiceTests {

    private final ShoppingAdviceService service = new ShoppingAdviceService();

    @Test
    void createsScenarioAwareHeadphoneAdvice() {
        ShoppingAdviceResult result = service.advise(new ShoppingAdviceRequest(
                "蓝牙耳机",
                new BigDecimal("100"),
                new BigDecimal("300"),
                "地铁通勤和会议",
                "降噪、长续航",
                "不要入耳式"));

        assertThat(result.budgetSummary()).contains("100").contains("300");
        assertThat(result.priorities()).contains("主动降噪和通透模式");
        assertThat(result.recommendations())
                .anyMatch(value -> value.contains("地铁通勤和会议"))
                .anyMatch(value -> value.contains("降噪、长续航"))
                .anyMatch(value -> value.contains("不要入耳式"));
        assertThat(result.notices()).allMatch(value -> !value.contains("http"));
    }

    @Test
    void fallsBackToGenericAdviceForUnknownCategory() {
        ShoppingAdviceResult result = service.advise(new ShoppingAdviceRequest(
                "桌面收纳架", null, null, "宿舍桌面", "容易清洁", "宽度小于40厘米"));

        assertThat(result.priorities()).contains("核心使用场景", "售后和退换政策");
        assertThat(result.checklist()).isNotEmpty();
    }

    @Test
    void rejectsInvalidBudgetRange() {
        assertThatThrownBy(() -> service.advise(new ShoppingAdviceRequest(
                "蓝牙耳机", new BigDecimal("500"), new BigDecimal("300"), "", "", "")))
                .isInstanceOf(ShoppingAdviceException.class)
                .hasMessageContaining("budget_min");
    }
}
