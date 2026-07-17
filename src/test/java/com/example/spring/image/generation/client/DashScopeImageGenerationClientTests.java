package com.example.spring.image.generation.client;

import com.example.spring.image.generation.ImageGenerationException;
import com.example.spring.image.generation.ImageGenerationRequest;
import com.example.spring.image.generation.ImageGenerationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DashScopeImageGenerationClientTests {

    @Test
    void sendsImageGenerationRequestAndParsesImageUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeImageGenerationClient client = new DashScopeImageGenerationClient(
                builder,
                new ObjectMapper(),
                "test-key",
                "https://dashscope.example.com/api/v1",
                "qwen-image-2.0-pro",
                "1024*1024",
                false,
                true);

        server.expect(requestTo("https://dashscope.example.com/api/v1/services/aigc/multimodal-generation/generation"))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("qwen-image-2.0-pro"))
                .andExpect(jsonPath("$.input.messages[0].role").value("user"))
                .andExpect(jsonPath("$.input.messages[0].content[0].text").value(containsString("赛博朋克风格的橘猫")))
                .andExpect(jsonPath("$.parameters.size").value("1024*1024"))
                .andExpect(jsonPath("$.parameters.watermark").value(false))
                .andExpect(jsonPath("$.parameters.prompt_extend").value(true))
                .andRespond(withSuccess("""
                        {
                          "output": {
                            "choices": [
                              {
                                "message": {
                                  "content": [
                                    {
                                      "image": "https://cdn.example.com/generated.png"
                                    }
                                  ]
                                }
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ImageGenerationResult result = client.generate(new ImageGenerationRequest("帮我画一只赛博朋克风格的橘猫"));

        assertThat(result.prompt()).contains("赛博朋克风格的橘猫");
        assertThat(result.imageUrl()).isEqualTo("https://cdn.example.com/generated.png");
        assertThat(result.fileName()).isEqualTo("generated.png");
        server.verify();
    }

    @Test
    void rejectsMissingApiKey() {
        DashScopeImageGenerationClient client = new DashScopeImageGenerationClient(
                RestClient.builder(),
                new ObjectMapper(),
                "",
                "https://dashscope.example.com/api/v1",
                "qwen-image-2.0-pro",
                "1024*1024",
                false,
                true);

        assertThatThrownBy(() -> client.generate(new ImageGenerationRequest("帮我画一只猫")))
                .isInstanceOf(ImageGenerationException.class)
                .hasMessage("未配置 DASHSCOPE_API_KEY");
    }
}
