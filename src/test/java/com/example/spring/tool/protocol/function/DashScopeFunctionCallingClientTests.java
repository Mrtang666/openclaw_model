package com.example.spring.tool.protocol.function;

import com.example.spring.tool.protocol.ConversationIntentDecision;
import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import com.example.spring.wechat.conversation.tools.WechatToolParameter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DashScopeFunctionCallingClientTests {

    @Test
    void sendsAgentLoopMessagesAndParsesFinalAssistantContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeFunctionCallingClient client = new DashScopeFunctionCallingClient(
                builder,
                new ObjectMapper(),
                new FunctionCallingToolSchemaConverter(),
                new FunctionCallingResponseParser(new ObjectMapper()),
                "test-key",
                "https://dashscope.example.com/compatible-mode/v1",
                "qwen3.7-max-2026-06-08");

        server.expect(requestTo("https://dashscope.example.com/compatible-mode/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].id").value("call_weather_1"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].function.name").value("weather"))
                .andExpect(jsonPath("$.messages[3].role").value("tool"))
                .andExpect(jsonPath("$.messages[3].tool_call_id").value("call_weather_1"))
                .andExpect(jsonPath("$.messages[3].content").value(containsString("sunny")))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "Hangzhou is sunny today, so it is suitable for going out."
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        FunctionCallingModelResponse response = client.chat(
                List.of(
                        FunctionCallingMessage.system("You are an agent."),
                        FunctionCallingMessage.user("Is Hangzhou suitable for going out today?"),
                        FunctionCallingMessage.assistantToolCalls(List.of(new FunctionCallingToolCall(
                                "call_weather_1",
                                "weather",
                                java.util.Map.of("city", "Hangzhou")))),
                        FunctionCallingMessage.tool("call_weather_1", "weather result: sunny")),
                List.of(new WechatToolDefinition(
                        "weather",
                        "query weather",
                        List.of(WechatToolParameter.requiredString("city", "city name", "Hangzhou")))))
                .orElseThrow();

        assertThat(response.hasToolCalls()).isFalse();
        assertThat(response.content()).contains("suitable for going out");
        server.verify();
    }

    @Test
    void sendsOpenAiCompatibleToolsRequestAndParsesToolCalls() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeFunctionCallingClient client = new DashScopeFunctionCallingClient(
                builder,
                new ObjectMapper(),
                new FunctionCallingToolSchemaConverter(),
                new FunctionCallingResponseParser(new ObjectMapper()),
                "test-key",
                "https://dashscope.example.com/compatible-mode/v1",
                "qwen3.7-max-2026-06-08");

        server.expect(requestTo("https://dashscope.example.com/compatible-mode/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("qwen3.7-max-2026-06-08"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.tool_choice").value("auto"))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.tools[0].function.name").value("weather"))
                .andExpect(jsonPath("$.tools[0].function.parameters.properties.city.description")
                        .value("要查询天气的中国城市名"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value(containsString("工具调用规划器")))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value(containsString("杭州天气")))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "tool_calls": [
                                  {
                                    "type": "function",
                                    "function": {
                                      "name": "weather",
                                      "arguments": "{\\"city\\":\\"杭州\\"}"
                                    }
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ConversationIntentDecision decision = client.planDecision(
                "帮我查杭州天气",
                "最近没有上下文",
                List.of(new WechatToolDefinition(
                        "weather",
                        "查询天气",
                        List.of(WechatToolParameter.requiredString("city", "要查询天气的中国城市名", "杭州")))))
                .orElseThrow();

        assertThat(decision.tasks()).singleElement()
                .satisfies(task -> {
                    assertThat(task.tool()).isEqualTo("weather");
                    assertThat(task.arguments()).containsEntry("city", "杭州");
                });
        server.verify();
    }
}
