package com.example.spring.document;

import com.example.spring.agent.AgentRequest;
import com.example.spring.bailian.BailianChatService;
import com.example.spring.memory.MemoryMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BailianDocumentTaskPlanner implements DocumentTaskPlanner {
    private final BailianChatService chatService;
    private final ObjectMapper objectMapper;

    @Autowired
    public BailianDocumentTaskPlanner(BailianChatService chatService) {
        this(chatService, new ObjectMapper());
    }

    BailianDocumentTaskPlanner(
        BailianChatService chatService,
        ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public DocumentTaskPlan plan(AgentRequest request, String normalizedInstruction)
        throws Exception {
        String prompt = """
            你是文档任务规划 Agent，只负责理解用户真正想完成的任务，不生成最终正文。
            请结合最新消息、最近对话、当前附件和最近文件，判断任务意图与信息来源。

            意图 intent：
            - CREATE：从零创作故事、文章、诗歌、文案、方案等新内容。
            - EXPORT：不改变内容，只把已有回复或文件原文导出为指定格式。
            - SUMMARIZE：总结或概括已有内容。
            - EXTRACT：提取重点、数据、结论或字段。
            - QUESTION：根据已有文件或回复回答问题。
            - TRANSFORM：翻译、改写、润色、续写、重组或基于材料创作。
            - CHAT：只是咨询文件格式知识或普通聊天，不需要生成文件。

            来源 source：
            - CURRENT_ATTACHMENT：本条消息新附带的文件。
            - PRIOR_ASSISTANT：用户明确说上文、上面的内容、刚才的回答或继续刚才内容。
            - RECENT_DOCUMENT：用户明确说这个文件、刚才文件，或任务必须基于最近文件。
            - NONE：从零创作或普通聊天，不使用历史文件。

            输出 output：PDF、DOCX 或 NONE。

            关键规则：
            1. “生成一个小故事以 PDF 输出”是 CREATE + NONE + PDF，不能使用最近文件。
            2. “根据这个文件写一个小故事并输出 PDF”是 TRANSFORM + RECENT_DOCUMENT + PDF。
            3. “把上面的内容输出 PDF”是 EXPORT + PRIOR_ASSISTANT + PDF。
            4. “总结这个文件”根据是否有当前附件选择 CURRENT_ATTACHMENT 或 RECENT_DOCUMENT。
            5. 最近存在文件不代表当前请求必须使用它；只有语义明确关联时才能选文件来源。
            6. title 是适合文件名和文档标题的简短中文标题；无法确定时留空。
            7. task 改写成完整、可执行的内容任务，去掉“输出 PDF/Word”等格式动作。
            8. 只返回 JSON，不使用 Markdown，不添加解释。

            JSON 格式：
            {
              "intent":"CREATE|EXPORT|SUMMARIZE|EXTRACT|QUESTION|TRANSFORM|CHAT",
              "source":"CURRENT_ATTACHMENT|PRIOR_ASSISTANT|RECENT_DOCUMENT|NONE",
              "output":"PDF|DOCX|NONE",
              "task":"最终内容任务",
              "title":"简短标题"
            }

            当前附件：%s
            最近可引用文件：%s
            最近对话：%s
            用户最新消息：%s
            规范化后的操作：%s
            """.formatted(
                fileNames(request.documents()),
                fileNames(request.referencedDocuments()),
                historyExcerpt(request.history()),
                request.text(),
                normalizedInstruction);

        String reply = chatService.chat(
            "document-task-planner-" + request.userId(), prompt, List.of());
        JsonNode root = objectMapper.readTree(extractJson(reply));
        return new DocumentTaskPlan(
            enumValue(DocumentTaskIntent.class, root.path("intent").asText(),
                DocumentTaskIntent.CHAT),
            enumValue(DocumentTaskSource.class, root.path("source").asText(),
                DocumentTaskSource.NONE),
            enumValue(DocumentOutputFormat.class, root.path("output").asText(),
                DocumentOutputFormat.NONE),
            root.path("task").asText(""),
            root.path("title").asText(""));
    }

    static String extractJson(String value) {
        if (value == null) throw new IllegalArgumentException("文档规划 Agent 没有返回结果");
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("文档规划 Agent 返回的不是 JSON");
        }
        return value.substring(start, end + 1);
    }

    private static String fileNames(List<DocumentAsset> documents) {
        if (documents == null || documents.isEmpty()) return "[]";
        return documents.stream().map(DocumentAsset::fileName).toList().toString();
    }

    private static String historyExcerpt(List<MemoryMessage> history) {
        if (history == null || history.isEmpty()) return "[]";
        int start = Math.max(0, history.size() - 6);
        return history.subList(start, history.size()).stream()
            .map(message -> message.role() + ":" + abbreviate(message.content(), 300))
            .toList().toString();
    }

    private static String abbreviate(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum) + "...";
    }

    private static <T extends Enum<T>> T enumValue(
        Class<T> type,
        String value,
        T fallback) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
