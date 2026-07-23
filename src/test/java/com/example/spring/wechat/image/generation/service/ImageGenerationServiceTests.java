package com.example.spring.wechat.image.generation.service;

import com.example.spring.wechat.image.generation.ImageGenerationClient;
import com.example.spring.wechat.image.generation.ImageGenerationException;
import com.example.spring.wechat.image.generation.model.ImageGenerationRequest;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ImageGenerationServiceTests {

    @Test
    void downloadsGeneratedImageBytes() {
        ImageGenerationClient client = mock(ImageGenerationClient.class);
        RestClient.Builder downloadBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(downloadBuilder).build();
        ImageGenerationService service = new ImageGenerationService(client, downloadBuilder);
        when(client.generate(new ImageGenerationRequest("帮我画一只橘猫")))
                .thenReturn(new ImageGenerationResult(
                        "帮我画一只橘猫",
                        "https://cdn.example.com/generated.png",
                        null,
                        "generated.png",
                        "image/png",
                        null,
                        null));

        server.expect(requestTo("https://cdn.example.com/generated.png"))
                .andExpect(method(GET))
                .andRespond(withSuccess("PNG-DATA", new MediaType("image", "png")));

        ImageGenerationResult result = service.generate(new ImageGenerationRequest("帮我画一只橘猫"));

        assertThat(result.imageUrl()).isEqualTo("https://cdn.example.com/generated.png");
        assertThat(result.fileName()).isEqualTo("generated.png");
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.imageBytes()).isEqualTo("PNG-DATA".getBytes(StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void downloadsGeneratedImageFromSignedUrlWithoutChangingEncodedSignature() {
        ImageGenerationClient client = mock(ImageGenerationClient.class);
        RestClient.Builder downloadBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(downloadBuilder).build();
        ImageGenerationService service = new ImageGenerationService(client, downloadBuilder);
        String signedImageUrl = "https://dashscope-7c2c.oss-accelerate.aliyuncs.com/7d/75/demo.png"
                + "?Expires=1784884125"
                + "&OSSAccessKeyId=test-key"
                + "&Signature=ECRZC8X3Wbj%2FZsJTb2C6Wc4p48I%3D";

        when(client.generate(new ImageGenerationRequest("生成一只赛博朋克风格的橘猫")))
                .thenReturn(new ImageGenerationResult(
                        "生成一只赛博朋克风格的橘猫",
                        signedImageUrl,
                        null,
                        null,
                        "image/png",
                        null,
                        null));

        server.expect(requestTo(URI.create(signedImageUrl)))
                .andExpect(method(GET))
                .andRespond(withSuccess("PNG-DATA", new MediaType("image", "png")));

        ImageGenerationResult result = service.generate(new ImageGenerationRequest("生成一只赛博朋克风格的橘猫"));

        assertThat(result.imageBytes()).isEqualTo("PNG-DATA".getBytes(StandardCharsets.UTF_8));
        assertThat(result.imageUrl()).isEqualTo(signedImageUrl);
        assertThat(result.fileName()).isEqualTo("demo.png");
        server.verify();
    }

    @Test
    void rejectsBlankPrompt() {
        ImageGenerationClient client = mock(ImageGenerationClient.class);
        ImageGenerationService service = new ImageGenerationService(client, RestClient.builder());

        assertThatThrownBy(() -> service.generate(new ImageGenerationRequest("   ")))
                .isInstanceOf(ImageGenerationException.class)
                .hasMessage("请告诉我你想生成什么图片");
    }
}
