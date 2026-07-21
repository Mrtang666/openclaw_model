package com.example.spring.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.POST;

class DashScopeChatClientTests {

    @Test
    void sendsOpenAiCompatibleStreamingRequestAndParsesAnswer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeChatClient client = new DashScopeChatClient(
                builder,
                new ObjectMapper(),
                "test-key",
                "https://dashscope.example.com/compatible-mode/v1",
                "qwen3.7-plus",
                true);
        String streamBody = """
                data: {"choices":[]}

                data: {"choices":[{"delta":{"reasoning_content":"先判断用户意图。"}}]}

                data: {"choices":[{"delta":{"content":"你好"}}]}

                data: {"choices":[{"delta":{"content":"，我是你的 AI 助手。"}}]}

                data: [DONE]
                """;

        server.expect(requestTo("https://dashscope.example.com/compatible-mode/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("qwen3.7-plus"))
                .andExpect(jsonPath("$.stream").value(true))
                .andExpect(jsonPath("$.extra_body.enable_thinking").value(true))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value(containsString("天气")))
                .andExpect(jsonPath("$.messages[0].content").value(containsString("图片生成")))
                .andExpect(jsonPath("$.messages[0].content").value(containsString("语音识别")))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("你是谁"))
                .andRespond(withSuccess(streamBody, new MediaType("text", "event-stream", StandardCharsets.UTF_8)));

        ChatReply reply = client.reply("你是谁");

        assertThat(reply.reasoningContent()).isEqualTo("先判断用户意图。");
        assertThat(reply.content()).isEqualTo("你好，我是你的 AI 助手。");
        server.verify();
    }

    @Test
    void rejectsMissingApiKey() {
        DashScopeChatClient client = new DashScopeChatClient(
                RestClient.builder(),
                new ObjectMapper(),
                "",
                "https://dashscope.example.com/compatible-mode/v1",
                "qwen3.7-plus",
                true);

        assertThatThrownBy(() -> client.reply("你是谁"))
                .isInstanceOf(ChatServiceException.class)
                .hasMessage("未配置 DASHSCOPE_API_KEY");
    }
}
