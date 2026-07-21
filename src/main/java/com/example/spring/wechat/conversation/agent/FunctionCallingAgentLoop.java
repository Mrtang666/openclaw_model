package com.example.spring.wechat.conversation.agent;

import com.example.spring.tool.protocol.function.DashScopeFunctionCallingClient;
import com.example.spring.tool.protocol.function.FunctionCallingMessage;
import com.example.spring.tool.protocol.function.FunctionCallingModelResponse;
import com.example.spring.tool.protocol.function.FunctionCallingToolCall;
import com.example.spring.tool.protocol.validation.ToolCallValidationResult;
import com.example.spring.tool.protocol.validation.ToolCallValidator;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import com.example.spring.wechat.conversation.tools.WechatToolRegistry;
import com.example.spring.wechat.conversation.tools.WechatToolRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 完整标准 Function Calling Agent 循环。
 *
 * <p>流程是：模型返回 tool_calls，Java 执行工具，把工具结果作为 tool message 回传模型；
 * 如果模型继续返回 tool_calls，就继续执行；直到模型返回最终文本或达到最大循环次数。</p>
 */
@Service
public class FunctionCallingAgentLoop {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallingAgentLoop.class);

    private static final String SYSTEM_PROMPT = """
            你是 OpenClaw 微信端 Agent。
            你可以根据用户需求调用工具，工具执行结果会以 tool message 形式返回给你。
            工作规则：
            1. 需要天气、图片、语音、音色、文件解析、文档生成等能力时，必须调用对应工具。
            2. 工具返回结果后，你要结合工具结果和上下文继续思考，必要时继续调用下一个工具。
            3. 当用户需求已经全部完成，不再调用工具，直接输出最终回复。
            4. 如果用户需求缺少关键信息，直接追问一个最关键的问题。
            5. 如果图片、语音或文件工具已经生成媒体内容，最终回复保持简短，不要重复描述内部执行过程。
            6. 多个需求按用户表达顺序逐个处理。
            """;

    private final DashScopeFunctionCallingClient client;
    private final WechatToolRegistry toolRegistry;
    private final ToolCallValidator toolCallValidator;
    private final int maxLoopRounds;

    @Autowired
    public FunctionCallingAgentLoop(
            DashScopeFunctionCallingClient client,
            WechatToolRegistry toolRegistry,
            ToolCallValidator toolCallValidator,
            @Value("${agent.tool-calling.max-loop-rounds:5}") int maxLoopRounds) {
        this.client = client;
        this.toolRegistry = toolRegistry;
        this.toolCallValidator = toolCallValidator;
        this.maxLoopRounds = Math.max(1, maxLoopRounds);
    }

    FunctionCallingAgentLoop(
            DashScopeFunctionCallingClient client,
            WechatToolRegistry toolRegistry,
            int maxLoopRounds) {
        this(client, toolRegistry, new ToolCallValidator(), maxLoopRounds);
    }

    public Optional<WechatReply> run(FunctionCallingAgentRequest request) {
        if (request == null || request.userText().isBlank()) {
            return Optional.empty();
        }

        List<WechatToolDefinition> toolDefinitions = toolRegistry.definitions();
        if (toolDefinitions.isEmpty()) {
            return Optional.empty();
        }

        List<FunctionCallingMessage> messages = new ArrayList<>();
        messages.add(FunctionCallingMessage.system(SYSTEM_PROMPT));
        messages.add(FunctionCallingMessage.user(userPrompt(request)));

        List<WechatReply.Part> visibleParts = new ArrayList<>();
        String previousToolResult = "";
        String rollingHistory = request.historyText();
        log.info("Function Calling Agent Loop 开始，userId={}, text={}",
                request.sessionKey(), preview(request.userText()));

        for (int round = 1; round <= maxLoopRounds; round++) {
            log.debug("Function Calling Agent Loop 第{}轮请求模型，userId={}", round, request.sessionKey());
            Optional<FunctionCallingModelResponse> response = client.chat(messages, toolDefinitions);
            if (response.isEmpty()) {
                log.warn("Function Calling Agent Loop 第{}轮模型无响应，userId={}", round, request.sessionKey());
                return Optional.empty();
            }

            FunctionCallingModelResponse modelResponse = response.get();
            if (!modelResponse.hasToolCalls()) {
                log.info("Function Calling Agent Loop 第{}轮得到最终回复，userId={}, reply={}",
                        round, request.sessionKey(), preview(modelResponse.content()));
                return Optional.of(finalReply(modelResponse.content(), visibleParts));
            }

            log.info("Function Calling Agent Loop 第{}轮返回工具调用，userId={}, tools={}",
                    round, request.sessionKey(), toolNames(modelResponse.toolCalls()));
            messages.add(FunctionCallingMessage.assistantToolCalls(modelResponse.toolCalls()));
            for (FunctionCallingToolCall toolCall : modelResponse.toolCalls()) {
                ToolCallValidationResult validation = toolCallValidator.validate(toolCall, toolDefinitions);
                if (!validation.valid()) {
                    AgentToolExecutionResult validationFailure = invalidToolCallResult(request, toolCall, validation);
                    messages.add(FunctionCallingMessage.tool(toolCall.id(), validationFailure.modelText()));
                    previousToolResult = validationFailure.modelText();
                    rollingHistory = appendRollingHistory(rollingHistory, toolCall.name(), validationFailure.modelText());
                    continue;
                }

                AgentToolExecutionResult toolResult = executeTool(request, toolCall, rollingHistory, previousToolResult);
                messages.add(FunctionCallingMessage.tool(toolCall.id(), toolResult.modelText()));
                visibleParts.addAll(toolResult.visibleParts());
                previousToolResult = toolResult.modelText();
                rollingHistory = appendRollingHistory(rollingHistory, toolCall.name(), toolResult.modelText());
            }
        }

        if (!visibleParts.isEmpty()) {
            return Optional.of(WechatReply.ordered(visibleParts));
        }
        return Optional.of(WechatReply.text("这次需求处理步骤比较多，我已经停止继续调用工具。你可以把需求拆短一点再发我。"));
    }

    private AgentToolExecutionResult invalidToolCallResult(
            FunctionCallingAgentRequest agentRequest,
            FunctionCallingToolCall toolCall,
            ToolCallValidationResult validation) {
        String result = "工具调用参数校验失败：" + validation.message();
        log.warn("Function Calling 工具调用校验失败，userId={}, tool={}, arguments={}, error={}",
                agentRequest.sessionKey(), toolCall.name(), toolCall.arguments(), validation.message());
        recordToolExecution(agentRequest, toolCall, result, "FAILED");
        return AgentToolExecutionResult.failure(toolCall.name(), toolCall.arguments(), result, validation.message());
    }

    private AgentToolExecutionResult executeTool(
            FunctionCallingAgentRequest agentRequest,
            FunctionCallingToolCall toolCall,
            String rollingHistory,
            String previousToolResult) {
        if (!toolRegistry.contains(toolCall.name())) {
            String result = "工具不存在：" + toolCall.name();
            recordToolExecution(agentRequest, toolCall, result, "FAILED");
            return AgentToolExecutionResult.failure(toolCall.name(), toolCall.arguments(), result, result);
        }

        Map<String, String> arguments = argumentsWithPreviousResult(toolCall, previousToolResult);
        try {
            WechatToolRequest request = new WechatToolRequest(
                    agentRequest.sessionKey(),
                    agentRequest.userText(),
                    arguments,
                    rollingHistory,
                    List.of(),
                    agentRequest.files(),
                    agentRequest.pendingImagePromptRecorder(),
                    agentRequest.generatedImageRecorder());
            WechatReply reply = toolRegistry.execute(toolCall.name(), request);
            List<WechatReply.Part> replyParts = toReplyParts(reply);
            String modelText = replyMemoryText(replyParts);
            if (modelText.isBlank()) {
                modelText = "工具已执行完成，但没有文本结果。";
            }
            recordToolExecution(agentRequest, toolCall, modelText, "SUCCESS");
            return AgentToolExecutionResult.success(
                    toolCall.name(), arguments, modelText, visibleParts(toolCall.name(), replyParts));
        } catch (RuntimeException exception) {
            String result = "工具执行失败：" + rootMessage(exception);
            log.warn("Function Calling Agent 工具执行失败，tool={}, error={}", toolCall.name(), rootMessage(exception));
            recordToolExecution(agentRequest, toolCall, result, "FAILED");
            return AgentToolExecutionResult.failure(toolCall.name(), arguments, result, rootMessage(exception));
        }
    }

    private Map<String, String> argumentsWithPreviousResult(FunctionCallingToolCall toolCall, String previousToolResult) {
        Map<String, String> arguments = new HashMap<>(toolCall.arguments());
        if ("voice_synthesis".equals(toolCall.name())
                && previousToolResult != null
                && !previousToolResult.isBlank()) {
            arguments.putIfAbsent("previous_result", previousToolResult);
            arguments.putIfAbsent("source", "current");
        }
        return arguments;
    }

    private List<WechatReply.Part> visibleParts(String toolName, List<WechatReply.Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return List.of();
        }
        boolean mediaTool = "image_generation".equals(toolName)
                || "voice_synthesis".equals(toolName)
                || "document_generation".equals(toolName);
        if (!mediaTool) {
            return List.of();
        }
        return parts.stream()
                .filter(part -> part != null && (part.hasImage() || part.hasVoice() || part.hasFile()))
                .toList();
    }

    private WechatReply finalReply(String finalContent, List<WechatReply.Part> visibleParts) {
        String content = finalContent == null ? "" : finalContent.strip();
        if (visibleParts == null || visibleParts.isEmpty()) {
            return WechatReply.text(content);
        }
        if (visibleParts.stream().anyMatch(WechatReply.Part::hasVoice)) {
            return WechatReply.ordered(visibleParts);
        }
        if (content.isBlank()) {
            return WechatReply.ordered(visibleParts);
        }

        List<WechatReply.Part> parts = new ArrayList<>();
        parts.add(WechatReply.Part.text(content));
        parts.addAll(visibleParts);
        return WechatReply.ordered(parts);
    }

    private List<WechatReply.Part> toReplyParts(WechatReply reply) {
        if (reply == null) {
            return List.of();
        }
        if (reply.parts() != null && !reply.parts().isEmpty()) {
            return reply.parts();
        }
        List<WechatReply.Part> parts = new ArrayList<>();
        if (reply.preImageTexts() != null) {
            reply.preImageTexts().stream()
                    .filter(text -> text != null && !text.isBlank())
                    .map(WechatReply.Part::text)
                    .forEach(parts::add);
        }
        if (reply.hasImage()) {
            parts.add(WechatReply.Part.image(reply.text(), reply.image()));
        } else if (reply.text() != null && !reply.text().isBlank()) {
            parts.add(WechatReply.Part.text(reply.text()));
        }
        return parts;
    }

    private String replyMemoryText(List<WechatReply.Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (WechatReply.Part part : parts) {
            if (part == null) {
                continue;
            }
            if (part.text() != null && !part.text().isBlank()) {
                appendDistinctMemoryText(text, part.text());
            }
            if (part.hasVoice()) {
                String transcript = part.voice() == null ? "" : part.voice().transcriptText();
                appendDistinctMemoryText(text, transcript == null || transcript.isBlank() ? "[已发送语音]" : transcript);
            }
            if (part.hasImage()) {
                appendDistinctMemoryText(text, "[已发送图片]");
            }
            if (part.hasFile()) {
                String fileName = part.file() == null ? "" : part.file().fileName();
                appendDistinctMemoryText(text, fileName.isBlank() ? "[已发送文件]" : "[已发送文件：" + fileName + "]");
            }
        }
        return text.toString().strip();
    }

    private void appendDistinctMemoryText(StringBuilder text, String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return;
        }
        String value = fragment.strip();
        if (text.indexOf(value) >= 0) {
            return;
        }
        if (!text.isEmpty()) {
            text.append(System.lineSeparator());
        }
        text.append(value);
    }

    private String appendRollingHistory(String history, String toolName, String result) {
        StringBuilder updated = new StringBuilder(history == null ? "" : history.strip());
        if (!updated.isEmpty()) {
            updated.append(System.lineSeparator());
        }
        updated.append("工具 ").append(toolName).append(" 结果：").append(result == null ? "" : result.strip());
        return updated.toString();
    }

    private void recordToolExecution(
            FunctionCallingAgentRequest request,
            FunctionCallingToolCall toolCall,
            String result,
            String status) {
        if (request.toolExecutionRecorder() != null) {
            request.toolExecutionRecorder().record(toolCall.name(), toolCall.arguments(), result, status);
        }
    }

    private String userPrompt(FunctionCallingAgentRequest request) {
        return """
                最近上下文：
                %s

                用户当前消息：
                %s
                """.formatted(request.historyText().isBlank() ? "无" : request.historyText(), request.userText());
    }

    private String toolNames(List<FunctionCallingToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "[]";
        }
        return toolCalls.stream()
                .map(FunctionCallingToolCall::name)
                .toList()
                .toString();
    }

    private String preview(String value) {
        String text = value == null ? "" : value.strip();
        return text.length() <= 120 ? text : text.substring(0, 117) + "...";
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

}
