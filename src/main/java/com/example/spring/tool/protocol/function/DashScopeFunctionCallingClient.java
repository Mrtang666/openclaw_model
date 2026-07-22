package com.example.spring.tool.protocol.function;

import com.example.spring.chat.ChatServiceException;
import com.example.spring.tool.protocol.ConversationIntentDecision;
import com.example.spring.tool.protocol.legacy.ToolCall;
import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 阿里百炼 OpenAI-compatible Function Calling 客户端。
 *
 * <p>负责发送标准 chat/completions 请求。它既保留旧的单轮规划入口，
 * 也提供完整 Agent Loop 需要的多轮 messages 入口。</p>
 */
@Component
public class DashScopeFunctionCallingClient {

    private static final String SYSTEM_PROMPT = """
            你是 OpenClaw 的工具调用规划器。
            请根据用户当前消息、最近上下文和可用工具 schema 判断是否需要调用工具。
            能用专用工具解决的问题必须返回 tool_calls，不要把专用工具需求当成普通聊天。
            如果用户需求信息不足，直接用自然中文追问一个最关键的问题，不要调用工具。
            多个需求要按用户表达顺序产生多个 tool_calls。
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final FunctionCallingToolSchemaConverter schemaConverter;
    private final FunctionCallingResponseParser responseParser;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public DashScopeFunctionCallingClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            FunctionCallingToolSchemaConverter schemaConverter,
            FunctionCallingResponseParser responseParser,
            @Value("${dashscope.api-key:}") String apiKey,
            @Value("${dashscope.base-url:}") String baseUrl,
            @Value("${openclaw.dashscope.model:${dashscope.model:qwen3.7-max-2026-06-08}}") String model) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.restClient = builder.baseUrl(this.baseUrl).build();
        this.objectMapper = objectMapper;
        this.schemaConverter = schemaConverter;
        this.responseParser = responseParser;
        this.apiKey = apiKey;
        this.model = model;
    }

    public Optional<ConversationIntentDecision> planDecision(
            String userText,
            String historyText,
            List<WechatToolDefinition> toolDefinitions) {
        if (userText == null || userText.isBlank() || toolDefinitions == null || toolDefinitions.isEmpty()) {
            return Optional.empty();
        }

        Optional<FunctionCallingModelResponse> response = chat(messages(userText, historyText), toolDefinitions);
        if (response.isEmpty()) {
            return Optional.empty();
        }

        FunctionCallingModelResponse modelResponse = response.get();
        if (modelResponse.hasToolCalls()) {
            return Optional.of(new ConversationIntentDecision(
                    modelResponse.toolCalls().stream()
                            .map(toolCall -> new ToolCall(toolCall.name(), toolCall.arguments()))
                            .toList(),
                    false,
                    ""));
        }
        if (!modelResponse.content().isBlank()) {
            return Optional.of(new ConversationIntentDecision(List.of(), true, modelResponse.content()));
        }
        return Optional.of(new ConversationIntentDecision(List.of(), false, ""));
    }

    public Optional<FunctionCallingModelResponse> chat(
            List<FunctionCallingMessage> messages,
            List<WechatToolDefinition> toolDefinitions) {
        if (messages == null || messages.isEmpty() || toolDefinitions == null || toolDefinitions.isEmpty()) {
            return Optional.empty();
        }

        validateConfiguration();
        try {
            String responseBody = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(requestBody(messages, toolDefinitions))
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, response) -> {
                        throw new ChatServiceException("百炼 Function Calling 接口返回错误：" + responseError(response));
                    })
                    .body(String.class);
            return responseParser.parseModelResponse(responseBody);
        } catch (ChatServiceException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ChatServiceException("百炼 Function Calling 接口暂时不可用", exception);
        }
    }

    private Map<String, Object> requestBody(
            List<FunctionCallingMessage> messages,
            List<WechatToolDefinition> toolDefinitions) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", serializeMessages(messages));
        body.put("tools", schemaConverter.convert(toolDefinitions));
        body.put("tool_choice", "auto");
        body.put("stream", false);
        return body;
    }

    private List<FunctionCallingMessage> messages(String userText, String historyText) {
        List<FunctionCallingMessage> messages = new ArrayList<>();
        messages.add(FunctionCallingMessage.system(SYSTEM_PROMPT));
        messages.add(FunctionCallingMessage.user(userPrompt(userText, historyText)));
        return messages;
    }

    private List<Map<String, Object>> serializeMessages(List<FunctionCallingMessage> messages) {
        return messages.stream()
                .map(this::serializeMessage)
                .toList();
    }

    private Map<String, Object> serializeMessage(FunctionCallingMessage message) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("role", message.role());
        if ("assistant".equals(message.role()) && !message.toolCalls().isEmpty()) {
            value.put("content", null);
            value.put("tool_calls", message.toolCalls().stream()
                    .map(this::serializeToolCall)
                    .toList());
            return value;
        }
        if ("tool".equals(message.role())) {
            value.put("tool_call_id", message.toolCallId());
        }
        value.put("content", message.content());
        return value;
    }

    private Map<String, Object> serializeToolCall(FunctionCallingToolCall toolCall) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", toolCall.name());
        function.put("arguments", argumentsJson(toolCall.arguments()));

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", toolCall.id());
        value.put("type", "function");
        value.put("function", function);
        return value;
    }

    private String argumentsJson(Map<String, String> arguments) {
        try {
            return objectMapper.writeValueAsString(arguments == null ? Map.of() : arguments);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String userPrompt(String userText, String historyText) {
        return """
                最近上下文：
                %s

                用户当前消息：
                %s
                """.formatted(historyText == null || historyText.isBlank() ? "无" : historyText.strip(), userText.strip());
    }

    private void validateConfiguration() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ChatServiceException("未配置 DASHSCOPE_API_KEY");
        }
        if (baseUrl.isBlank()) {
            throw new ChatServiceException("未配置 DASHSCOPE_BASE_URL，请在 .env 中填写你的模型 Host 完整地址");
        }
    }

    private String responseError(ClientHttpResponse response) {
        try {
            String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (body.isBlank()) {
                return response.getStatusCode().toString();
            }
            return response.getStatusCode() + "：" + body;
        } catch (IOException exception) {
            return "HTTP 响应读取失败";
        }
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String result = value.strip();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}

