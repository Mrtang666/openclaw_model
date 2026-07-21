package com.example.spring.agent;

import com.example.spring.bailian.BailianChatService;
import com.example.spring.document.DocumentAsset;
import com.example.spring.document.DocumentOutputFormat;
import com.example.spring.document.DocumentTaskIntent;
import com.example.spring.document.DocumentTaskPlan;
import com.example.spring.document.DocumentTaskSource;
import com.example.spring.memory.MemoryMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BailianIntentRoutingAgent implements IntentRoutingAgent {
    private final BailianChatService chatService;
    private final ObjectMapper objectMapper;

    @Autowired
    public BailianIntentRoutingAgent(BailianChatService chatService) {
        this(chatService, new ObjectMapper());
    }

    BailianIntentRoutingAgent(
        BailianChatService chatService,
        ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public IntentRoutingDecision route(AgentRequest request) throws Exception {
        String prompt = """
            你是微信多 Agent 机器人的顶层意图路由 Agent。
            你的职责是理解用户真正要完成的目标，并选择一个或多个同级业务 Agent。
            不要回答用户问题，不要执行业务任务，只返回 JSON。

            可选 Agent：
            - CHAT：普通对话、知识问答、从零创作但不要求输出文件。
            - WEATHER：查询实时天气、温度、湿度、风力或日期天气。
            - VISION：识别、描述或分析图片。
            - IMAGE_GENERATION：生成图片，或修改当前/历史图片。
            - DOCUMENT：读取文件、基于文件问答，或者生成 PDF/Word 等文件。

            路由规则：
            1. 五个业务 Agent 地位相同，根据当前目标选择，不因为最近使用过某模块就强行延续。
            2. “生成一个小故事以 PDF 格式输出”选择 DOCUMENT，因为最终交付物是文件；
               文档意图为 CREATE，来源为 NONE，绝不能使用最近文件。
            3. “给我讲一个小故事”选择 CHAT。
            4. “根据这个文件写一个故事并输出 PDF”选择 DOCUMENT，来源为文件。
            5. “把上面的回答输出 PDF”选择 DOCUMENT，来源为 PRIOR_ASSISTANT。
            6. “PDF 是什么”是知识问答，选择 CHAT，不要因为出现 PDF 就选择 DOCUMENT。
            7. 用户要求先识别图片再据此生成图片时，agents 依次返回 VISION、IMAGE_GENERATION。
            8. 当前存在附件或历史文件只是上下文线索，不代表必须选择 DOCUMENT。
            9. 如果选择 DOCUMENT，必须同时填写 documentTask；其他 Agent 的 documentTask 为 null。
            10. 只返回 JSON，不使用 Markdown，不添加解释。

            JSON 格式：
            {
              "agents":["CHAT"],
              "documentTask":{
                "intent":"CREATE|EXPORT|SUMMARIZE|EXTRACT|QUESTION|TRANSFORM|CHAT",
                "source":"CURRENT_ATTACHMENT|PRIOR_ASSISTANT|RECENT_DOCUMENT|NONE",
                "output":"PDF|DOCX|NONE",
                "task":"去掉文件格式动作后的内容任务",
                "title":"适合文件名的简短标题"
              }
            }

            当前文字：%s
            当前图片数量：%d
            当前新附件：%s
            最近可引用图片数量：%d
            最近可引用文件：%s
            最近对话：%s
            """.formatted(
                request.text(),
                request.attachedImageCount(),
                fileNames(request.documents()),
                request.referencedImages().size(),
                fileNames(request.referencedDocuments()),
                historyExcerpt(request.history()));

        String reply = chatService.chat(
            "intent-router-" + request.userId(), prompt, List.of());
        JsonNode root = objectMapper.readTree(extractJson(reply));
        List<AgentType> steps = parseAgents(root.path("agents"));
        DocumentTaskPlan documentTask = steps.contains(AgentType.DOCUMENT)
            ? parseDocumentTask(root.path("documentTask")) : null;
        return new IntentRoutingDecision(new AgentPlan(steps), documentTask, true);
    }

    static String extractJson(String value) {
        if (value == null) throw new IllegalArgumentException("意图路由 Agent 没有返回结果");
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("意图路由 Agent 返回的不是 JSON");
        }
        return value.substring(start, end + 1);
    }

    private static List<AgentType> parseAgents(JsonNode node) {
        List<AgentType> steps = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                AgentType type = enumValue(AgentType.class, item.asText(), null);
                if (type != null && !steps.contains(type)) steps.add(type);
            }
        }
        if (steps.isEmpty()) steps.add(AgentType.CHAT);
        if (steps.size() > 1
            && !(steps.equals(List.of(AgentType.VISION, AgentType.IMAGE_GENERATION)))) {
            return List.of(steps.get(0));
        }
        return List.copyOf(steps);
    }

    private static DocumentTaskPlan parseDocumentTask(JsonNode node) {
        if (node == null || !node.isObject()) {
            return new DocumentTaskPlan(
                DocumentTaskIntent.CHAT,
                DocumentTaskSource.NONE,
                DocumentOutputFormat.NONE,
                "",
                "");
        }
        return new DocumentTaskPlan(
            enumValue(DocumentTaskIntent.class, node.path("intent").asText(),
                DocumentTaskIntent.CHAT),
            enumValue(DocumentTaskSource.class, node.path("source").asText(),
                DocumentTaskSource.NONE),
            enumValue(DocumentOutputFormat.class, node.path("output").asText(),
                DocumentOutputFormat.NONE),
            node.path("task").asText(""),
            node.path("title").asText(""));
    }

    private static String fileNames(List<DocumentAsset> documents) {
        if (documents == null || documents.isEmpty()) return "[]";
        return documents.stream().map(DocumentAsset::fileName).toList().toString();
    }

    private static String historyExcerpt(List<MemoryMessage> history) {
        if (history == null || history.isEmpty()) return "[]";
        int start = Math.max(0, history.size() - 8);
        return history.subList(start, history.size()).stream()
            .map(message -> message.role() + ":" + abbreviate(message.content(), 240))
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
