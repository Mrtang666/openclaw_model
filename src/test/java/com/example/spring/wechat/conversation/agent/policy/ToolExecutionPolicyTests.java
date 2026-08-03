package com.example.spring.wechat.conversation.agent.policy;

import com.example.spring.tool.protocol.function.FunctionCallingToolCall;
import com.example.spring.wechat.conversation.WechatConversationMode;
import com.example.spring.wechat.conversation.agent.FunctionCallingAgentRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutionPolicyTests {

    private final ToolExecutionPolicy policy = new ToolExecutionPolicy();

    @Test
    void skipsWebSearchWhenRagAlreadyHasEvidence() {
        FunctionCallingAgentRequest request = request("介绍一下这个项目", "[知识1] 项目说明");
        FunctionCallingToolCall toolCall = toolCall("web_search", Map.of("query", "项目说明"));

        assertThat(policy.shouldSkipWebSearchBecauseRagHasEvidence(request, toolCall, toolCall.arguments()))
                .isTrue();
    }

    @Test
    void allowsWebSearchWhenUserRequestsFreshInformation() {
        FunctionCallingAgentRequest request = request("查一下这个项目最新公开资料", "[知识1] 项目说明");
        FunctionCallingToolCall toolCall = toolCall("web_search", Map.of("query", "项目说明"));

        assertThat(policy.shouldSkipWebSearchBecauseRagHasEvidence(request, toolCall, toolCall.arguments()))
                .isFalse();
    }

    @Test
    void allowsWebSearchWhenArgumentsRequestFreshInformation() {
        FunctionCallingAgentRequest request = request("查一下这个项目", "[知识1] 项目说明");
        FunctionCallingToolCall toolCall = toolCall("web_search", Map.of("query", "project current price"));

        assertThat(policy.shouldSkipWebSearchBecauseRagHasEvidence(request, toolCall, toolCall.arguments()))
                .isFalse();
    }

    @Test
    void doesNotSkipNonWebSearchOrEmptyRagContext() {
        FunctionCallingAgentRequest request = request("介绍一下这个项目", "[知识1] 项目说明");

        assertThat(policy.shouldSkipWebSearchBecauseRagHasEvidence(
                request, toolCall("knowledge_query", Map.of("query", "项目说明")), Map.of("query", "项目说明")))
                .isFalse();
        assertThat(policy.shouldSkipWebSearchBecauseRagHasEvidence(
                request("介绍一下这个项目", ""), toolCall("web_search", Map.of("query", "项目说明")), Map.of("query", "项目说明")))
                .isFalse();
    }

    @Test
    void createsVoiceSynthesisSignatureFromFirstAvailableTargetText() {
        assertThat(policy.voiceSynthesisSignature("voice_synthesis", Map.of(
                "voice", "Alloy",
                "target_text", "  你好，   OpenClaw  ",
                "previous_result", "不应该使用")))
                .isEqualTo("voice_synthesis|alloy|你好， OpenClaw");

        assertThat(policy.voiceSynthesisSignature("voice_synthesis", Map.of(
                "message", "请播报这句话")))
                .isEqualTo("voice_synthesis||请播报这句话");

        assertThat(policy.voiceSynthesisSignature("image_generation", Map.of("target_text", "你好")))
                .isBlank();
    }

    @Test
    void createsStableToolCallSignatureFromSortedArguments() {
        String first = policy.toolCallSignature("web_search", Map.of("b", "  二  ", "a", "一"));
        String second = policy.toolCallSignature("web_search", Map.of("a", "一", "b", "二"));

        assertThat(first).isEqualTo("web_search|{a=一, b=二}");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void createsNormalizedToolFailureSignature() {
        assertThat(policy.toolFailureSignature(
                "map_search",
                Map.of("query", "  深圳   南山  "),
                "  地址   存在歧义  "))
                .isEqualTo("map_search|{query=深圳 南山}|地址 存在歧义");
    }

    private FunctionCallingAgentRequest request(String userText, String ragContext) {
        return new FunctionCallingAgentRequest(
                "user-1",
                userText,
                "history",
                ragContext,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                WechatConversationMode.GENERAL);
    }

    private FunctionCallingToolCall toolCall(String name, Map<String, String> arguments) {
        return new FunctionCallingToolCall("call-1", name, arguments);
    }
}
