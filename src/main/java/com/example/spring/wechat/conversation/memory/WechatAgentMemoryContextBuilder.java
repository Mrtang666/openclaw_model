package com.example.spring.wechat.conversation.memory;

import com.example.spring.wechat.memory.model.ConversationTurn;
import com.example.spring.wechat.memory.model.WechatConversationMemory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 微信 Agent 上下文构造器。
 *
 * <p>这个类只负责把内存对象里的摘要、最近对话、媒体记忆、工具状态和待追问信息，
 * 整理成大模型更容易读取的分层文本。它不访问数据库，也不修改记忆内容。</p>
 */
@Component
public class WechatAgentMemoryContextBuilder {

    private static final int DEFAULT_RECENT_TURN_LIMIT = 10;
    private static final String EMPTY_CONTEXT = "无";

    public String build(WechatConversationMemory memory) {
        return build(memory, "");
    }

    public String build(WechatConversationMemory memory, String resourceContext) {
        if (memory == null) {
            return EMPTY_CONTEXT;
        }

        StringBuilder context = new StringBuilder();
        appendSummary(context, memory);
        appendRecentTurns(context, memory.recentTurns(DEFAULT_RECENT_TURN_LIMIT));
        appendMediaMemory(context, memory);
        appendResourceContext(context, resourceContext);
        appendToolState(context, memory);
        appendPendingClarification(context, memory);

        return context.isEmpty() ? EMPTY_CONTEXT : context.toString().strip();
    }

    private void appendSummary(StringBuilder context, WechatConversationMemory memory) {
        memory.conversationSummary().ifPresent(summary ->
                appendSection(context, "conversation_summary / 会话摘要", summary));
    }

    private void appendRecentTurns(StringBuilder context, List<ConversationTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return;
        }

        StringBuilder text = new StringBuilder();
        for (ConversationTurn turn : turns) {
            if (turn == null) {
                continue;
            }
            appendLine(text, "用户：" + safeText(turn.userText()));
            appendLine(text, "助手：" + safeText(turn.assistantText()));
        }
        appendSection(context, "recent_turns / 最近对话", text.toString());
    }

    private void appendMediaMemory(StringBuilder context, WechatConversationMemory memory) {
        StringBuilder text = new StringBuilder();
        memory.lastImagePrompt().ifPresent(value -> appendLine(text, "最近图片线索：" + value));
        memory.lastPendingImagePrompt().ifPresent(value -> appendLine(text, "待确认图片提示词：" + value));
        memory.lastFileName().ifPresent(value -> appendLine(text, "最近文件名：" + value));
        memory.lastFileFormat().ifPresent(value -> appendLine(text, "最近文件格式：" + value));
        memory.lastFileSummary().ifPresent(value -> appendLine(text, "最近文件摘要：" + value));
        memory.pendingFileQuestion().ifPresent(value -> appendLine(text, "待补充文件需求：" + value));

        if (!text.isEmpty()) {
            appendSection(context, "media_memory / 媒体记忆", text.toString());
        }
    }

    private void appendResourceContext(StringBuilder context, String resourceContext) {
        if (resourceContext != null && !resourceContext.isBlank()) {
            appendSection(context, "resource_context / 可用资源", resourceContext);
        }
    }

    private void appendToolState(StringBuilder context, WechatConversationMemory memory) {
        StringBuilder text = new StringBuilder();
        memory.lastWeatherCity().ifPresent(value -> appendLine(text, "最近查询天气城市：" + value));

        if (!text.isEmpty()) {
            appendSection(context, "tool_state / 工具状态", text.toString());
        }
    }

    private void appendPendingClarification(StringBuilder context, WechatConversationMemory memory) {
        StringBuilder text = new StringBuilder();
        memory.pendingClarificationUserText().ifPresent(value -> appendLine(text, "上一轮未完成需求：" + value));
        memory.pendingClarificationQuestion().ifPresent(value -> appendLine(text, "上一轮追问：" + value));
        memory.pendingClarificationToolName().ifPresent(value -> appendLine(text, "关联工具：" + value));
        if (!memory.pendingClarificationMissingFields().isEmpty()) {
            appendLine(text, "缺失字段：" + String.join(", ", memory.pendingClarificationMissingFields()));
        }

        if (!text.isEmpty()) {
            appendSection(context, "pending_clarification / 待追问状态", text.toString());
        }
    }

    private void appendSection(StringBuilder context, String title, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (!context.isEmpty()) {
            context.append(System.lineSeparator()).append(System.lineSeparator());
        }
        context.append("【").append(title).append("】")
                .append(System.lineSeparator())
                .append(content.strip());
    }

    private void appendLine(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(System.lineSeparator());
        }
        builder.append(value.strip());
    }

    private String safeText(String value) {
        return value == null ? "" : value.strip();
    }
}
