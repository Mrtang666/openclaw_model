package com.example.spring.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.document.DocumentAsset;
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
        assertThat(route("我想要一张卢浮宫的风景照", 0).steps())
            .containsExactly(AgentType.IMAGE_GENERATION);
        assertThat(route("给我来一张星空壁纸", 0).steps())
            .containsExactly(AgentType.IMAGE_GENERATION);
        assertThat(route("我需要一张活动海报", 0).steps())
            .containsExactly(AgentType.IMAGE_GENERATION);
        assertThat(route("我想要看一下这张图片", 1).steps())
            .containsExactly(AgentType.VISION);
        assertThat(route("按照这张图生成一张插画", 1).steps())
            .containsExactly(AgentType.VISION, AgentType.IMAGE_GENERATION);
        assertThat(route("把上文输出为PDF", 0).steps())
            .containsExactly(AgentType.DOCUMENT);

        AgentRequest attachedDocument = new AgentRequest(
            "user", 4L, "总结这个文件", List.of(), 0,
            List.of(), List.of(),
            List.of(new DocumentAsset("id", "a.txt", "text/plain",
                new byte[] {1}, "content", "content")),
            1, List.of());
        assertThat(router.route(attachedDocument).steps())
            .containsExactly(AgentType.DOCUMENT);

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

        AgentRequest removeCrowd = historicalVision.withText("去除里面的人群");
        assertThat(router.route(removeCrowd).steps())
            .containsExactly(AgentType.IMAGE_GENERATION);

        AgentRequest improveQuotedImage = historicalVision.withText("把引用的图片优化一下");
        assertThat(router.route(improveQuotedImage).steps())
            .containsExactly(AgentType.IMAGE_GENERATION);
    }

    private AgentPlan route(String text, int imageCount) {
        return router.route(new AgentRequest("user", 1L, text, List.of(), imageCount));
    }
}
