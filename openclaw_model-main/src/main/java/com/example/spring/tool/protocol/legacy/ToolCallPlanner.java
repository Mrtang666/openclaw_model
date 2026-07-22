package com.example.spring.tool.protocol.legacy;

import com.example.spring.chat.ChatService;
import com.example.spring.tool.protocol.ConversationIntentDecision;
import com.example.spring.tool.protocol.ConversationToolPlanner;
import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import com.example.spring.wechat.conversation.tools.WechatToolParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
/**
 * 调用大语言模型，让模型根据用户输入和工具列表，生成结构化任务计划
 */
import java.util.List;
import java.util.Optional;

@Service
public class ToolCallPlanner implements ConversationToolPlanner {

    private static final Logger log = LoggerFactory.getLogger(ToolCallPlanner.class);

    private final ChatService chatService;
    private final ToolCallPlanParser parser;

    public ToolCallPlanner(ChatService chatService, ToolCallPlanParser parser) {
        this.chatService = chatService;
        this.parser = parser;
    }

    public Optional<ToolPlan> plan(String userText, List<WechatToolDefinition> toolDefinitions) {
        return plan(userText, toolDefinitions, "");
    }

    public Optional<ToolPlan> plan(String userText, List<WechatToolDefinition> toolDefinitions, String historyText) {
        return planDecision(userText, toolDefinitions, historyText)
                .map(ConversationIntentDecision::tasks)
                .map(ToolPlan::new);
    }

    @Override
    public Optional<ConversationIntentDecision> planDecision(
            String userText,
            List<WechatToolDefinition> toolDefinitions,
            String historyText) {
        if (userText == null || userText.isBlank() || toolDefinitions == null || toolDefinitions.isEmpty()) {
            return Optional.empty();
        }

        try {
            String modelOutput = chatService.reply(buildPrompt(userText, toolDefinitions, historyText));
            return parser.parseDecision(modelOutput);
        } catch (RuntimeException exception) {
            log.warn("工具调用规划失败，text={}, error={}", preview(userText), rootMessage(exception));
            return Optional.empty();
        }
    }

    private String buildPrompt(String userText, List<WechatToolDefinition> toolDefinitions, String historyText) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是智能助手的任务拆解与工具规划器。").append('\n')
                .append("任务：先整体理解用户需求，再一次性拆解成有顺序的任务。").append('\n')
                .append("只输出 JSON，不要输出解释、Markdown、代码块或多余文本。").append('\n')
                .append("JSON 格式必须是：").append('\n')
                .append("{\"needs_clarification\":false,\"clarification_question\":\"\",\"tasks\":[{\"tool\":\"工具名\",\"arguments\":{\"参数名\":\"参数值\"}}]}").append('\n')
                .append("规则：").append('\n')
                .append("1. 如果用户有多个需求，按用户表达顺序生成多个 tasks。").append('\n')
                .append("2. 先判断每个需求是否信息完整；如果缺少关键条件，设置 needs_clarification=true，并给出自然完整的 clarification_question。").append('\n')
                .append("3. 一旦 needs_clarification=true，本轮先不要执行任务。").append('\n')
                .append("4. 每个 task 只能选择一个已注册工具。").append('\n')
                .append("5. 需要先优化图片提示词再生成图片时，仍然使用 image_generation，并设置 optimize_prompt=true。").append('\n')
                .append("6. 用户要求“等我确认后再生成图片”时，设置 wait_for_approval=true。").append('\n')
                .append("7. 天气问题使用 weather，并尽量提取 city。").append('\n')
                .append("8. 不能把专用工具需求归类到 chat；chat 只放普通闲聊或总结类 message。").append('\n')
                .append("9. 如果用户当前话语依赖前文，例如“可以”“继续”“换一种”“偏好美食”，必须结合最近对话判断真实意图。").append('\n')
                .append("10. 如果某个需求看起来模糊，但结合上下文可以合理补全，就直接补全后放入 tasks；不稳就追问。").append('\n')
                .append("11. 用户明确要求“用语音回复、发语音、语音播报、读给我听”时，必须使用 voice_synthesis。").append('\n')
                .append("12. 如果用户要求朗读上一轮助手回复、上一条内容、刚才那段、发给我、再说一遍，voice_synthesis 的 arguments 必须设置 source=previous；如果是当前新生成的语音内容，则 source=current。").append('\n')
                .append("13. 如果语音回复依赖天气、图片、计划或普通问答等前置结果，先按用户表达顺序调用前置工具，再调用 voice_synthesis。").append('\n')
                .append("14. voice_recognition 是内部语音识别工具，只在当前消息确实包含微信语音附件时使用；普通文本里提到“语音”不要调用它。").append('\n')
                .append("15. 用户要求修改、筛选、试听、确认语音音色时使用 voice_style；例如“换个温柔女声”“试听第一个”“把第五个当成音色”“就用刚才那个”“再成熟一点”“修改音色”。这类后续短句要结合最近音色候选和试听上下文判断，不要当成普通聊天。").append('\n')
                .append("已注册工具：").append('\n');

        for (WechatToolDefinition definition : toolDefinitions) {
            prompt.append("- ")
                    .append(definition.name())
                    .append("：")
                    .append(definition.description())
                    .append("；参数 schema：")
                    .append(formatParameters(definition.parameters()))
                    .append('\n');
        }

        String history = historyText == null || historyText.isBlank() ? "无" : historyText.strip();
        prompt.append("最近对话：").append('\n')
                .append(history).append('\n')
                .append("用户原话：").append(userText).append('\n')
                .append("请输出任务拆解 JSON：");
        return prompt.toString();
    }

    private String formatParameters(List<WechatToolParameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "无";
        }
        return parameters.stream()
                .map(this::formatParameter)
                .toList()
                .toString();
    }

    private String formatParameter(WechatToolParameter parameter) {
        StringBuilder value = new StringBuilder();
        value.append(parameter.name())
                .append("(type=")
                .append(parameter.type())
                .append(", required=")
                .append(parameter.required());
        if (!parameter.description().isBlank()) {
            value.append(", description=").append(parameter.description());
        }
        if (!parameter.allowedValues().isEmpty()) {
            value.append(", allowed=").append(String.join("|", parameter.allowedValues()));
        }
        if (!parameter.example().isBlank()) {
            value.append(", example=").append(parameter.example());
        }
        value.append(")");
        return value.toString();
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
