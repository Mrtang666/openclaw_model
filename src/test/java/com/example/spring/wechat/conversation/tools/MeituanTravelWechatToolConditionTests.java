package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.travel.client.MeituanTravelClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MeituanTravelWechatToolConditionTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(MeituanTravelClient.class, () -> mock(MeituanTravelClient.class))
            .withUserConfiguration(MeituanTravelWechatTool.class);

    @Test
    void doesNotRegisterToolWhenFeatureIsDisabled() {
        contextRunner
                .withPropertyValues("meituan.travel.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MeituanTravelWechatTool.class));
    }

    @Test
    void registersToolWhenFeatureIsEnabled() {
        contextRunner
                .withPropertyValues("meituan.travel.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(MeituanTravelWechatTool.class));
    }
}
