package com.example.spring.wechat.conversation.memory;

import com.example.spring.wechat.memory.model.ConversationTurn;
import com.example.spring.wechat.memory.model.WechatConversationMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private static final int DEFAULT_MAX_CONTEXT_CHARS = 12_000;
    private static final String EMPTY_CONTEXT = "无";
    private static final String TRUNCATION_MARKER = "...[上下文已截断]";

    private final int maxContextChars;

    public WechatAgentMemoryContextBuilder() {
        this(DEFAULT_MAX_CONTEXT_CHARS);
    }

    @Autowired
    public WechatAgentMemoryContextBuilder(
            @Value("${wechat.agent.memory.max-context-chars:12000}") Integer maxContextChars) {
        this.maxContextChars = normalizeLimit(maxContextChars);
    }

    WechatAgentMemoryContextBuilder(int maxContextChars) {
        this.maxContextChars = normalizeLimit(maxContextChars);
    }

    public String build(WechatConversationMemory memory) {
        return build(memory, "");
    }

    public String build(WechatConversationMemory memory, String resourceContext) {
        if (memory == null) {
            return EMPTY_CONTEXT;
        }

        List<Section> sections = new ArrayList<>();
        addPendingClarification(sections, memory);
        addResourceContext(sections, resourceContext);
        addRecentTurns(sections, memory.recentTurns(DEFAULT_RECENT_TURN_LIMIT));
        addMediaMemory(sections, memory);
        addToolState(sections, memory);
        addSummary(sections, memory);

        StringBuilder context = new StringBuilder();
        for (Section section : sections) {
            appendBoundedSection(context, section);
        }
        return context.isEmpty() ? EMPTY_CONTEXT : context.toString().strip();
    }

    private void addSummary(List<Section> sections, WechatConversationMemory memory) {
        memory.conversationSummary().ifPresent(summary ->
                addSection(sections, "conversation_summary / 会话摘要", summary));
    }

    private void addRecentTurns(List<Section> sections, List<ConversationTurn> turns) {
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
        addSection(sections, "recent_turns / 最近对话", text.toString());
    }

    private void addMediaMemory(List<Section> sections, WechatConversationMemory memory) {
        StringBuilder text = new StringBuilder();
        memory.lastImagePrompt().ifPresent(value -> appendLine(text, "最近图片线索：" + value));
        memory.lastPendingImagePrompt().ifPresent(value -> appendLine(text, "待确认图片提示词：" + value));
        memory.lastFileName().ifPresent(value -> appendLine(text, "最近文件名：" + value));
        memory.lastFileFormat().ifPresent(value -> appendLine(text, "最近文件格式：" + value));
        memory.lastFileSummary().ifPresent(value -> appendLine(text, "最近文件摘要：" + value));
        memory.pendingFileQuestion().ifPresent(value -> appendLine(text, "待补充文件需求：" + value));

        if (!text.isEmpty()) {
            addSection(sections, "media_memory / 媒体记忆", text.toString());
        }
    }

    private void addResourceContext(List<Section> sections, String resourceContext) {
        addSection(sections, "resource_context / 可用资源", resourceContext);
    }

    private void addToolState(List<Section> sections, WechatConversationMemory memory) {
        StringBuilder text = new StringBuilder();
        memory.lastWeatherCity().ifPresent(value -> appendLine(text, "最近查询天气城市：" + value));

        if (!text.isEmpty()) {
            addSection(sections, "tool_state / 工具状态", text.toString());
        }
    }

    private void addPendingClarification(List<Section> sections, WechatConversationMemory memory) {
        StringBuilder text = new StringBuilder();
        memory.pendingClarificationUserText().ifPresent(value -> appendLine(text, "上一轮未完成需求：" + value));
        memory.pendingClarificationQuestion().ifPresent(value -> appendLine(text, "上一轮追问：" + value));
        memory.pendingClarificationToolName().ifPresent(value -> appendLine(text, "关联工具：" + value));
        if (!memory.pendingClarificationMissingFields().isEmpty()) {
            appendLine(text, "缺失字段：" + String.join(", ", memory.pendingClarificationMissingFields()));
        }

        if (!text.isEmpty()) {
            addSection(sections, "pending_clarification / 待追问状态", text.toString());
        }
    }

    private void addSection(List<Section> sections, String title, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        sections.add(new Section(title, content.strip()));
    }

    private void appendBoundedSection(StringBuilder context, Section section) {
        String value = formatSection(section);
        String separator = context.isEmpty() ? "" : System.lineSeparator() + System.lineSeparator();
        int remaining = maxContextChars - context.length() - separator.length();
        if (remaining <= 0) {
            appendTruncationMarker(context);
            return;
        }
        if (value.length() <= remaining) {
            context.append(separator).append(value);
            return;
        }
        context.append(separator).append(truncate(value, remaining));
    }

    private String formatSection(Section section) {
        return "【" + section.title() + "】"
                + System.lineSeparator()
                + section.content();
    }

    private String truncate(String value, int limit) {
        if (limit <= 0) {
            return "";
        }
        if (limit <= TRUNCATION_MARKER.length()) {
            return TRUNCATION_MARKER.substring(0, limit);
        }
        int contentLimit = limit - TRUNCATION_MARKER.length();
        return value.substring(0, Math.min(value.length(), contentLimit)).stripTrailing() + TRUNCATION_MARKER;
    }

    private void appendTruncationMarker(StringBuilder context) {
        if (context.indexOf(TRUNCATION_MARKER) >= 0) {
            return;
        }
        int markerStart = Math.max(0, maxContextChars - TRUNCATION_MARKER.length());
        if (context.length() > markerStart) {
            context.replace(markerStart, context.length(), TRUNCATION_MARKER);
        } else if (context.length() + TRUNCATION_MARKER.length() <= maxContextChars) {
            context.append(TRUNCATION_MARKER);
        }
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

    private static int normalizeLimit(Integer value) {
        if (value == null || value <= TRUNCATION_MARKER.length()) {
            return DEFAULT_MAX_CONTEXT_CHARS;
        }
        return value;
    }

    private record Section(String title, String content) {
    }
}
