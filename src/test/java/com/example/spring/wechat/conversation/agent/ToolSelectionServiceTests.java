package com.example.spring.wechat.conversation.agent;

import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import com.example.spring.wechat.conversation.tools.WechatToolParameter;
import com.example.spring.wechat.model.ImageSourceType;
import com.example.spring.wechat.model.VideoSourceType;
import com.example.spring.wechat.model.WechatIncomingFile;
import com.example.spring.wechat.model.WechatIncomingImage;
import com.example.spring.wechat.model.WechatIncomingVideo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSelectionServiceTests {

    @Test
    void selectsOnlyRelevantWeatherToolFromLargeToolSet() {
        ToolSelectionService selector = new ToolSelectionService(true, 2);
        List<WechatToolDefinition> selected = selector.select(
                request("帮我查杭州明天天气", "", 0, 0, 0),
                List.of(
                        tool("document_generation", "generate document", "title"),
                        tool("email_send", "send email", "recipient"),
                        tool("image_generation", "generate image", "prompt"),
                        tool("weather", "query weather forecast", "city"),
                        tool("web_search", "search web", "query")));

        assertThat(selected).extracting(WechatToolDefinition::name)
                .contains("weather")
                .doesNotContain("image_generation", "email_send");
        assertThat(selected).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void keepsImageToolWhenRequestContainsImages() {
        ToolSelectionService selector = new ToolSelectionService(true, 2);
        List<WechatToolDefinition> selected = selector.select(
                request("分析这张图", "", 0, 1, 0),
                List.of(
                        tool("weather", "query weather forecast", "city"),
                        tool("image_understanding", "understand image", "question"),
                        tool("email_send", "send email", "recipient")));

        assertThat(selected).extracting(WechatToolDefinition::name)
                .contains("image_understanding")
                .doesNotContain("weather");
    }

    private FunctionCallingAgentRequest request(
            String userText,
            String ragContext,
            int files,
            int images,
            int videos) {
        return new FunctionCallingAgentRequest(
                "session",
                userText,
                "",
                ragContext,
                files(files),
                images(images),
                videos(videos),
                (a, b) -> {
                },
                (a, b) -> {
                },
                (a, b, c, d) -> {
                });
    }

    private List<WechatIncomingFile> files(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new WechatIncomingFile("", "file-" + index + ".txt", "text/plain", new byte[]{1}, 1L, "", ""))
                .toList();
    }

    private List<WechatIncomingImage> images(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new WechatIncomingImage(ImageSourceType.WECHAT_ATTACHMENT, new byte[]{1}))
                .toList();
    }

    private List<WechatIncomingVideo> videos(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new WechatIncomingVideo(
                        VideoSourceType.WECHAT_ATTACHMENT,
                        "",
                        new byte[]{1},
                        "video/mp4",
                        "video-" + index + ".mp4",
                        1L,
                        null,
                        "",
                        "",
                        ""))
                .toList();
    }

    private WechatToolDefinition tool(String name, String description, String parameter) {
        return new WechatToolDefinition(
                name,
                description,
                List.of(WechatToolParameter.optionalString(parameter, parameter, parameter)));
    }
}
