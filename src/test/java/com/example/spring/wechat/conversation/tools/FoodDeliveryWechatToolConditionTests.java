package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.food.service.FoodOrderOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FoodDeliveryWechatToolConditionTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(FoodOrderOrchestrationService.class,
                    () -> mock(FoodOrderOrchestrationService.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(FoodDeliveryWechatTool.class);

    @Test
    void doesNotRegisterToolWhenFeatureIsDisabled() {
        contextRunner
                .withPropertyValues("food.delivery.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FoodDeliveryWechatTool.class));
    }

    @Test
    void registersToolWhenFeatureIsEnabled() {
        contextRunner
                .withPropertyValues("food.delivery.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(FoodDeliveryWechatTool.class));
    }
}
