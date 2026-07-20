package com.example.spring.tool.protocol;


/**
 * 标准工具调用协议层，负责承载和解析大模型工具计划。
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ToolCallPlanParser {

    private final ObjectMapper objectMapper;

    public ToolCallPlanParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<ToolPlan> parse(String modelOutput) {
        return parseDecision(modelOutput)
                .map(ConversationIntentDecision::tasks)
                .map(ToolPlan::new);
    }

    public Optional<ConversationIntentDecision> parseDecision(String modelOutput) {
        if (modelOutput == null || modelOutput.isBlank()) {
            return Optional.empty();
        }

        String json = extractJsonObject(modelOutput);
        if (json.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            boolean needsClarification = root.path("needs_clarification").asBoolean(false);
            String clarificationQuestion = nodeToString(root.path("clarification_question"));

            JsonNode tasksNode = root.path("tasks");
            if (!tasksNode.isArray()) {
                return Optional.of(new ConversationIntentDecision(
                        List.of(),
                        needsClarification,
                        clarificationQuestion));
            }

            java.util.List<ToolCall> tasks = new java.util.ArrayList<>();
            for (JsonNode taskNode : tasksNode) {
                String tool = taskNode.path("tool").asText("");
                JsonNode argumentsNode = taskNode.path("arguments");
                Map<String, String> arguments = new LinkedHashMap<>();
                if (argumentsNode.isObject()) {
                    argumentsNode.fields().forEachRemaining(entry ->
                            arguments.put(entry.getKey(), nodeToString(entry.getValue())));
                }
                if (!tool.isBlank()) {
                    tasks.add(new ToolCall(tool, arguments));
                }
            }
            return Optional.of(new ConversationIntentDecision(tasks, needsClarification, clarificationQuestion));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String extractJsonObject(String value) {
        String text = value.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return text.substring(start, end + 1);
    }

    private String nodeToString(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean() || node.isNumber()) {
            return node.asText();
        }
        return node.toString();
    }
}

