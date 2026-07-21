package com.example.spring.tool.protocol.function;

import com.example.spring.tool.protocol.ConversationIntentDecision;
import com.example.spring.tool.protocol.legacy.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Function Calling 响应解析器。
 *
 * <p>负责读取 OpenAI-compatible 响应里的 tool_calls，
 * 并转换成项目内部可执行的工具调用对象。</p>
 */
@Component
public class FunctionCallingResponseParser {

    private final ObjectMapper objectMapper;

    public FunctionCallingResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<ConversationIntentDecision> parse(String responseBody) {
        Optional<FunctionCallingModelResponse> modelResponse = parseModelResponse(responseBody);
        if (modelResponse.isEmpty()) {
            return Optional.empty();
        }

        FunctionCallingModelResponse response = modelResponse.get();
        if (response.hasToolCalls()) {
            return Optional.of(new ConversationIntentDecision(
                    response.toolCalls().stream()
                            .map(toolCall -> new ToolCall(toolCall.name(), toolCall.arguments()))
                            .toList(),
                    false,
                    ""));
        }
        if (!response.content().isBlank()) {
            return Optional.of(new ConversationIntentDecision(List.of(), true, response.content()));
        }
        return Optional.of(new ConversationIntentDecision(List.of(), false, ""));
    }

    public Optional<FunctionCallingModelResponse> parseModelResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("choices").path(0).path("message");
            if (message.isMissingNode() || message.isNull()) {
                return Optional.empty();
            }

            List<FunctionCallingToolCall> toolCalls = parseToolCalls(message.path("tool_calls"));
            String content = nodeToString(message.path("content")).strip();
            return Optional.of(new FunctionCallingModelResponse(content, toolCalls));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private List<FunctionCallingToolCall> parseToolCalls(JsonNode toolCallsNode) {
        if (!toolCallsNode.isArray()) {
            return List.of();
        }

        List<FunctionCallingToolCall> tasks = new ArrayList<>();
        for (JsonNode toolCallNode : toolCallsNode) {
            JsonNode functionNode = toolCallNode.path("function");
            String id = nodeToString(toolCallNode.path("id"));
            String name = nodeToString(functionNode.path("name"));
            if (name.isBlank()) {
                continue;
            }
            tasks.add(new FunctionCallingToolCall(id, name, parseArguments(functionNode.path("arguments"))));
        }
        return tasks;
    }

    private Map<String, String> parseArguments(JsonNode argumentsNode) {
        if (argumentsNode == null || argumentsNode.isMissingNode() || argumentsNode.isNull()) {
            return Map.of();
        }

        try {
            if (argumentsNode.isObject()) {
                return objectToStringMap(argumentsNode);
            }
            String rawArguments = argumentsNode.asText("");
            if (rawArguments.isBlank()) {
                return Map.of();
            }
            JsonNode parsed = objectMapper.readTree(rawArguments);
            return parsed.isObject() ? objectToStringMap(parsed) : Map.of();
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, String> objectToStringMap(JsonNode objectNode) {
        Map<String, String> arguments = new LinkedHashMap<>();
        objectNode.fields().forEachRemaining(entry ->
                arguments.put(entry.getKey(), nodeToString(entry.getValue())));
        return arguments;
    }

    private String nodeToString(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual() || node.isBoolean() || node.isNumber()) {
            return node.asText();
        }
        return node.toString();
    }
}

