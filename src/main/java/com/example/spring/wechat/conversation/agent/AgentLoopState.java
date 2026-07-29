package com.example.spring.wechat.conversation.agent;

import com.example.spring.tool.protocol.function.FunctionCallingMessage;
import com.example.spring.wechat.bot.WechatReply;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class AgentLoopState {

    private static final int MAX_ROLLING_TOOL_RESULTS = 6;

    private final List<FunctionCallingMessage> messages = new ArrayList<>();
    private final List<WechatReply.Part> visibleParts = new ArrayList<>();
    private final Map<String, String> successfulToolResults = new HashMap<>();
    private final Deque<String> rollingToolResults = new ArrayDeque<>();
    private final Set<String> executedVoiceSynthesisSignatures = new HashSet<>();
    private final Set<String> failedToolSignatures = new HashSet<>();
    private final String initialHistory;
    private String previousToolResult = "";
    private String lastToolFailure = "";
    private AgentLoopStopReason stopReason = AgentLoopStopReason.NONE;

    private AgentLoopState(String systemPrompt, String userPrompt, String historyText) {
        messages.add(FunctionCallingMessage.system(systemPrompt));
        messages.add(FunctionCallingMessage.user(userPrompt));
        initialHistory = historyText == null ? "" : historyText;
    }

    static AgentLoopState start(String systemPrompt, String userPrompt, String historyText) {
        return new AgentLoopState(systemPrompt, userPrompt, historyText);
    }

    List<FunctionCallingMessage> messages() {
        return messages;
    }

    List<WechatReply.Part> visibleParts() {
        return visibleParts;
    }

    String previousToolResult() {
        return previousToolResult;
    }

    String lastToolFailure() {
        return lastToolFailure;
    }

    String rollingHistory() {
        StringBuilder history = new StringBuilder(initialHistory.strip());
        for (String result : rollingToolResults) {
            if (!history.isEmpty()) {
                history.append(System.lineSeparator());
            }
            history.append(result);
        }
        return history.toString();
    }

    AgentLoopStopReason stopReason() {
        return stopReason;
    }

    void stop(AgentLoopStopReason reason) {
        stopReason = reason == null ? AgentLoopStopReason.NONE : reason;
    }

    boolean addVoiceSynthesisSignature(String signature) {
        return signature != null && !signature.isBlank() && executedVoiceSynthesisSignatures.add(signature);
    }

    boolean addFailedToolSignature(String signature) {
        return signature != null && !signature.isBlank() && failedToolSignatures.add(signature);
    }

    Optional<String> successfulToolResult(String signature) {
        if (signature == null || signature.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(successfulToolResults.get(signature));
    }

    void rememberSuccessfulToolResult(String signature, String modelText) {
        if (signature != null && !signature.isBlank()) {
            successfulToolResults.put(signature, normalize(modelText));
        }
    }

    void recordSkippedToolCall(String toolName, String modelText) {
        previousToolResult = normalize(modelText);
        appendRollingHistory(toolName, previousToolResult);
    }

    void recordToolResult(String toolName, String modelText) {
        previousToolResult = normalize(modelText);
        if (!previousToolResult.isBlank()) {
            lastToolFailure = "";
        }
        appendRollingHistory(toolName, previousToolResult);
    }

    void recordToolFailure(String toolName, String modelText) {
        previousToolResult = normalize(modelText);
        lastToolFailure = previousToolResult;
        appendRollingHistory(toolName, previousToolResult);
    }

    void addVisibleParts(List<WechatReply.Part> parts) {
        if (parts != null) {
            visibleParts.addAll(parts);
        }
    }

    boolean hasVisibleParts() {
        return !visibleParts.isEmpty();
    }

    void replaceExistingMediaOfSameType(List<WechatReply.Part> incomingParts) {
        if (visibleParts.isEmpty() || incomingParts == null || incomingParts.isEmpty()) {
            return;
        }
        if (containsImagePart(incomingParts)) {
            visibleParts.removeIf(part -> part != null && part.hasImage());
        }
        if (containsVoicePart(incomingParts)) {
            visibleParts.removeIf(part -> part != null && part.hasVoice());
        }
        if (containsFilePart(incomingParts)) {
            visibleParts.removeIf(part -> part != null && part.hasFile());
        }
    }

    private void appendRollingHistory(String toolName, String result) {
        rollingToolResults.addLast("Tool " + toolName + " result: " + (result == null ? "" : result.strip()));
        while (rollingToolResults.size() > MAX_ROLLING_TOOL_RESULTS) {
            rollingToolResults.removeFirst();
        }
    }

    private boolean containsVoicePart(List<WechatReply.Part> parts) {
        return parts != null && parts.stream().anyMatch(part -> part != null && part.hasVoice());
    }

    private boolean containsImagePart(List<WechatReply.Part> parts) {
        return parts != null && parts.stream().anyMatch(part -> part != null && part.hasImage());
    }

    private boolean containsFilePart(List<WechatReply.Part> parts) {
        return parts != null && parts.stream().anyMatch(part -> part != null && part.hasFile());
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
