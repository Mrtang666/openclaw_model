package com.example.spring.tool.protocol;

import com.example.spring.tool.protocol.function.FunctionCallingToolPlanner;
import com.example.spring.tool.protocol.legacy.ToolCallPlanner;
import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 可配置的工具规划器路由层。
 *
 * <p>项目启动后通过 {@code agent.tool-calling.mode} 决定使用哪套协议：
 * 默认使用旧版 prompt-json，配置为 function-calling 时切到新版原生 tool_calls。
 * 这样微信主流程不需要知道底层实现，后续也可以继续扩展更多规划协议。</p>
 */
@Primary
@Service
public class ConfigurableConversationToolPlanner implements ConversationToolPlanner {

    public static final String MODE_PROMPT_JSON = "prompt-json";
    public static final String MODE_FUNCTION_CALLING = "function-calling";

    private final ConversationToolPlanner promptJsonPlanner;
    private final ConversationToolPlanner functionCallingPlanner;
    private final String mode;

    @Autowired
    public ConfigurableConversationToolPlanner(
            ToolCallPlanner promptJsonPlanner,
            FunctionCallingToolPlanner functionCallingPlanner,
            @Value("${agent.tool-calling.mode:prompt-json}") String mode) {
        this((ConversationToolPlanner) promptJsonPlanner, functionCallingPlanner, mode);
    }

    ConfigurableConversationToolPlanner(
            ConversationToolPlanner promptJsonPlanner,
            ConversationToolPlanner functionCallingPlanner,
            String mode) {
        this.promptJsonPlanner = promptJsonPlanner;
        this.functionCallingPlanner = functionCallingPlanner;
        this.mode = normalize(mode);
    }

    @Override
    public Optional<ConversationIntentDecision> planDecision(
            String userText,
            List<WechatToolDefinition> toolDefinitions,
            String historyText) {
        return activePlanner().planDecision(userText, toolDefinitions, historyText);
    }

    private ConversationToolPlanner activePlanner() {
        if (MODE_FUNCTION_CALLING.equals(mode)) {
            return functionCallingPlanner;
        }
        return promptJsonPlanner;
    }

    private String normalize(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return MODE_PROMPT_JSON;
        }
        return rawMode.trim().toLowerCase(Locale.ROOT);
    }
}
