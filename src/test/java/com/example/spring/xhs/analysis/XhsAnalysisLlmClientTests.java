package com.example.spring.xhs.analysis;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class XhsAnalysisLlmClientTests {

    @Test
    void usesDedicatedNonThinkingJsonRequestAndReturnsUsage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://dashscope.example/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"enable_thinking\":false")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"stream\":false")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"json_object\"")))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\\"sentiment\\\":\\\"NEUTRAL\\\"}"}}],
                         "usage":{"prompt_tokens":120,"completion_tokens":30,"total_tokens":150}}
                        """, MediaType.APPLICATION_JSON));

        XhsAnalysisLlmClient client = new XhsAnalysisLlmClient(
                builder, "https://dashscope.example/v1/", "secret", "qwen-plus", 300);

        XhsAnalysisLlmClient.Response response = client.analyze("analyze this post");

        assertThat(response.content()).contains("NEUTRAL");
        assertThat(response.promptTokens()).isEqualTo(120);
        assertThat(response.completionTokens()).isEqualTo(30);
        assertThat(response.totalTokens()).isEqualTo(150);
        server.verify();
    }
}
