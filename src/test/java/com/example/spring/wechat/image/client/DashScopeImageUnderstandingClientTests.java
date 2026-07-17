package com.example.spring.wechat.image.client;

import com.example.spring.wechat.client.ImageSourceType;
import com.example.spring.wechat.client.WechatIncomingImage;
import com.example.spring.wechat.image.model.ImageAnalysisRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DashScopeImageUnderstandingClientTests {

    @Test
    void sendsMultimodalRequestWithAttachmentAndUrlImages() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeImageUnderstandingClient client = new DashScopeImageUnderstandingClient(
                builder,
                new ObjectMapper(),
                "test-key",
                "https://dashscope.example.com/compatible-mode/v1",
                "qwen3.7-plus",
                true);
        String streamBody = """
                data: {"choices":[]}

                data: {"choices":[{"delta":{"content":"图片里是一只猫。"}}]}

                data: [DONE]
                """;

        ImageAnalysisRequest request = new ImageAnalysisRequest(
                "帮我看看这张图",
                List.of(
                        new WechatIncomingImage(
                                ImageSourceType.WECHAT_ATTACHMENT,
                                "wechat://msg-1/image-1",
                                samplePngBytes(),
                                "image/png",
                                "photo.png",
                                2,
                                2,
                                "COLOR"),
                        new WechatIncomingImage(
                                ImageSourceType.TEXT_URL,
                                "https://example.com/demo.png",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)));

        server.expect(requestTo("https://dashscope.example.com/compatible-mode/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("qwen3.7-plus"))
                .andExpect(jsonPath("$.stream").value(true))
                .andExpect(jsonPath("$.extra_body.enable_thinking").value(true))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content[0].type").value("text"))
                .andExpect(jsonPath("$.messages[1].content[0].text").value(containsString("帮我看看这张图")))
                .andExpect(jsonPath("$.messages[1].content[1].type").value("image_url"))
                .andExpect(jsonPath("$.messages[1].content[1].image_url.url").value(containsString("data:image/png;base64")))
                .andExpect(jsonPath("$.messages[1].content[2].type").value("image_url"))
                .andExpect(jsonPath("$.messages[1].content[2].image_url.url").value("https://example.com/demo.png"))
                .andRespond(withSuccess(streamBody, new MediaType("text", "event-stream", StandardCharsets.UTF_8)));

        String reply = client.reply(request);

        assertThat(reply).isEqualTo("图片里是一只猫。");
        server.verify();
    }

    private byte[] samplePngBytes() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(0, 1, Color.GREEN.getRGB());
        image.setRGB(1, 0, Color.BLUE.getRGB());
        image.setRGB(1, 1, Color.WHITE.getRGB());

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
