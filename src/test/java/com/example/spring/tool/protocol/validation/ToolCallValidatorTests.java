package com.example.spring.tool.protocol.validation;

import com.example.spring.tool.protocol.function.FunctionCallingToolCall;
import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import com.example.spring.wechat.conversation.tools.WechatToolParameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallValidatorTests {

    @Test
    void rejectsMissingRequiredArgument() {
        ToolCallValidator validator = new ToolCallValidator();

        ToolCallValidationResult result = validator.validate(
                new FunctionCallingToolCall("call_1", "weather", Map.of()),
                List.of(new WechatToolDefinition(
                        "weather",
                        "query weather",
                        List.of(WechatToolParameter.requiredString("city", "city name", "Hangzhou")))));

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("city");
    }

    @Test
    void rejectsInvalidEnumArgument() {
        ToolCallValidator validator = new ToolCallValidator();

        ToolCallValidationResult result = validator.validate(
                new FunctionCallingToolCall("call_1", "voice_style", Map.of("action", "delete_all")),
                List.of(new WechatToolDefinition(
                        "voice_style",
                        "change voice style",
                        List.of(WechatToolParameter.optionalEnum(
                                "action",
                                "operation",
                                List.of("search", "preview", "confirm"),
                                "search")))));

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("action").contains("search").contains("preview").contains("confirm");
    }
}
