package com.example.spring.wechat.conversation.memory;

import com.example.spring.wechat.memory.model.ConversationTurn;
import com.example.spring.wechat.memory.model.WechatConversationMemory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 微信 Agent 上下文构造器。
 *
 * <p>这个类只负责把内存对象中已经存在的短期对话、滚动摘要、媒体摘要和工具状态整理成一段
 * 大模型更容易理解的文本；它不直接访问数据库，也不修改记忆内容，避免和持久化逻辑强耦合。</p>
 */
@Component
public class WechatAgentMemoryContextBuilder {

    private static final int DEFAULT_RECENT_TURN_LIMIT = 10;

    public String build(WechatConversationMemory memory) {
        if (memory == null) {
            return "无";
        }

        StringBuilder context = new StringBuilder();
        appendSummary(context, memory);
        appendRecentTurns(context, memory.recentTurns(DEFAULT_RECENT_TURN_LIMIT));
        appendMediaMemory(context, memory);
        appendToolState(context, memory);

        return context.isEmpty() ? "无" : context.toString().strip();
    }

    private void appendSummary(StringBuilder context, WechatConversationMemory memory) {
        memory.conversationSummary().ifPresent(summary -> appendSection(context, "滚动摘要", summary));
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
        appendSection(context, "最近对话", text.toString());
    }

    private void appendMediaMemory(StringBuilder context, WechatConversationMemory memory) {
        StringBuilder text = new StringBuilder();
        memory.lastImagePrompt().ifPresent(value -> appendLine(text, "最近图片：" + value));
        memory.lastPendingImagePrompt().ifPresent(value -> appendLine(text, "待确认图片提示词：" + value));
        memory.lastFileName().ifPresent(value -> appendLine(text, "最近文件名：" + value));
        memory.lastFileFormat().ifPresent(value -> appendLine(text, "最近文件格式：" + value));
        memory.lastFileSummary().ifPresent(value -> appendLine(text, "最近文件摘要：" + value));
        memory.pendingFileQuestion().ifPresent(value -> appendLine(text, "待补充文件需求：" + value));

        if (!text.isEmpty()) {
            appendSection(context, "媒体记忆", text.toString());
        }
    }

    private void appendToolState(StringBuilder context, WechatConversationMemory memory) {
        StringBuilder text = new StringBuilder();
        memory.lastWeatherCity().ifPresent(value -> appendLine(text, "最近查询天气城市：" + value));
        memory.pendingClarificationQuestion().ifPresent(value -> appendLine(text, "待澄清问题：" + value));

        if (!text.isEmpty()) {
            appendSection(context, "工具状态", text.toString());
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
