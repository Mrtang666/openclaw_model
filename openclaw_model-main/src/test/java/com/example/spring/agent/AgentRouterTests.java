package com.example.spring.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRouterTests {
    private final AgentRouter router = new AgentRouter();

    @Test
    void routesEverySupportedInputAndAllowsCombinedImageGeneration() {
        assertThat(route("你好", 0).steps()).containsExactly(AgentType.CHAT);
        assertThat(route("查询江苏无锡滨湖区天气", 0).steps())
            .containsExactly(AgentType.WEATHER);
        assertThat(route("识别 https://example.com/photo.png", 0).steps())
            .containsExactly(AgentType.VISION);
        assertThat(route("帮我识别这张图片 https://example.com/signed?id=1", 0).steps())
            .containsExactly(AgentType.VISION);
        assertThat(route("画一张江南水乡", 0).steps())
            .containsExactly(AgentType.IMAGE_GENERATION);
        assertThat(route("按照这张图生成一张插画", 1).steps())
            .containsExactly(AgentType.VISION, AgentType.IMAGE_GENERATION);

        AgentRequest historicalEdit = new AgentRequest(
            "user",
            2L,
            "把上一张改成夜景",
            List.of(),
            0,
            List.of(),
            List.of(new ImageAsset(new byte[] {1}, "image/png", "history.png")));
        assertThat(router.route(historicalEdit).steps())
            .containsExactly(AgentType.IMAGE_GENERATION);

        AgentRequest historicalVision = new AgentRequest(
            "user",
            3L,
            "上一张图片里有什么",
            List.of(),
            0,
            List.of(),
            historicalEdit.referencedImages());
        assertThat(router.route(historicalVision).steps())
            .containsExactly(AgentType.VISION);

        AgentRequest continuedEdit = historicalVision.withText("再亮一点");
        assertThat(router.route(continuedEdit).steps())
            .containsExactly(AgentType.IMAGE_GENERATION);
    }

    private AgentPlan route(String text, int imageCount) {
        return router.route(new AgentRequest("user", 1L, text, List.of(), imageCount));
    }
}
