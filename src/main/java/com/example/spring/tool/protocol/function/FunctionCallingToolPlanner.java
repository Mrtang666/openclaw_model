package com.example.spring.tool.protocol.function;

import com.example.spring.tool.protocol.ConversationIntentDecision;
import com.example.spring.tool.protocol.ConversationToolPlanner;
import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Function Calling 工具规划器。
 * 这是旧 ToolCallPlanner 的并行实现，便于后续用配置把微信主流程灰度切换到原生 tool_calls。
 */
@Service
public class FunctionCallingToolPlanner implements ConversationToolPlanner {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallingToolPlanner.class);

    private final DashScopeFunctionCallingClient functionCallingClient;

    public FunctionCallingToolPlanner(DashScopeFunctionCallingClient functionCallingClient) {
        this.functionCallingClient = functionCallingClient;
    }

    @Override
    public Optional<ConversationIntentDecision> planDecision(
            String userText,
            List<WechatToolDefinition> toolDefinitions,
            String historyText) {
        try {
            return functionCallingClient.planDecision(userText, historyText, toolDefinitions);
        } catch (RuntimeException exception) {
            log.warn("Function Calling 工具规划失败，text={}, error={}", preview(userText), rootMessage(exception));
            return Optional.empty();
        }
    }

    private String preview(String value) {
        String text = value == null ? "" : value.strip();
        return text.length() <= 80 ? text : text.substring(0, 77) + "...";
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
