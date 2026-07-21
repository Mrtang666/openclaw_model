package com.example.spring.tool.protocol.function;

import com.example.spring.tool.protocol.ConversationIntentDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FunctionCallingResponseParserTests {

    @Test
    void parsesModelResponseWithToolCallIdsForAgentLoop() {
        FunctionCallingResponseParser parser = new FunctionCallingResponseParser(new ObjectMapper());

        FunctionCallingModelResponse response = parser.parseModelResponse("""
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "tool_calls": [
                          {
                            "id": "call_weather_1",
                            "type": "function",
                            "function": {
                              "name": "weather",
                              "arguments": "{\\"city\\":\\"Hangzhou\\",\\"question\\":\\"is it good to go out\\"}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """).orElseThrow();

        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.content()).isBlank();
        assertThat(response.toolCalls()).singleElement()
                .satisfies(toolCall -> {
                    assertThat(toolCall.id()).isEqualTo("call_weather_1");
                    assertThat(toolCall.name()).isEqualTo("weather");
                    assertThat(toolCall.arguments())
                            .containsEntry("city", "Hangzhou")
                            .containsEntry("question", "is it good to go out");
                });
    }

    @Test
    void parsesFinalAssistantContentForAgentLoop() {
        FunctionCallingResponseParser parser = new FunctionCallingResponseParser(new ObjectMapper());

        FunctionCallingModelResponse response = parser.parseModelResponse("""
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "Hangzhou is suitable for going out today."
                      }
                    }
                  ]
                }
                """).orElseThrow();

        assertThat(response.hasToolCalls()).isFalse();
        assertThat(response.content()).isEqualTo("Hangzhou is suitable for going out today.");
    }

    @Test
    void parsesOpenAiCompatibleToolCallsIntoConversationDecision() {
        FunctionCallingResponseParser parser = new FunctionCallingResponseParser(new ObjectMapper());

        ConversationIntentDecision decision = parser.parse("""
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "tool_calls": [
                          {
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "weather",
                              "arguments": "{\\"city\\":\\"杭州\\",\\"question\\":\\"适合出门吗\\"}"
                            }
                          },
                          {
                            "id": "call_2",
                            "type": "function",
                            "function": {
                              "name": "voice_synthesis",
                              "arguments": "{\\"source\\":\\"previous\\"}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """).orElseThrow();

        assertThat(decision.needsClarification()).isFalse();
        assertThat(decision.tasks()).hasSize(2);
        assertThat(decision.tasks().get(0).tool()).isEqualTo("weather");
        assertThat(decision.tasks().get(0).arguments()).containsEntry("city", "杭州");
        assertThat(decision.tasks().get(1).tool()).isEqualTo("voice_synthesis");
        assertThat(decision.tasks().get(1).arguments()).containsEntry("source", "previous");
    }

    @Test
    void treatsPlainAssistantContentWithoutToolCallsAsClarificationQuestion() {
        FunctionCallingResponseParser parser = new FunctionCallingResponseParser(new ObjectMapper());

        ConversationIntentDecision decision = parser.parse("""
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "你想生成 PDF 还是 Word 文档？"
                      }
                    }
                  ]
                }
                """).orElseThrow();

        assertThat(decision.needsClarification()).isTrue();
        assertThat(decision.clarificationQuestion()).isEqualTo("你想生成 PDF 还是 Word 文档？");
        assertThat(decision.tasks()).isEmpty();
    }
}
