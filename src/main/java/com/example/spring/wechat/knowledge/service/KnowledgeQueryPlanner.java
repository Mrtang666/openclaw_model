package com.example.spring.wechat.knowledge.service;

import com.example.spring.chat.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库检索查询规划器。
 *
 * <p>优先让大模型把用户问题改写成 2-3 个更适合向量检索的查询；
 * 如果模型不可用或返回格式不合法，则使用规则降级，至少保留原问题。</p>
 */
@Service
public class KnowledgeQueryPlanner {

    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KnowledgeQueryPlanner(ChatService chatService) {
        this.chatService = chatService;
    }

    public List<String> planQueries(String question) {
        String text = question == null ? "" : question.strip();
        if (text.isBlank()) {
            return List.of();
        }
        if (chatService != null) {
            try {
                List<String> modelQueries = parse(chatService.reply(prompt(text)));
                if (!modelQueries.isEmpty()) {
                    return modelQueries;
                }
            } catch (RuntimeException ignored) {
                // 模型规划失败时走规则降级，不能影响知识库检索主流程。
            }
        }
        return ruleQueries(text);
    }

    private List<String> ruleQueries(String question) {
        List<String> queries = new ArrayList<>();
        queries.add(question);
        String compact = question
                .replace("请", "")
                .replace("帮我", "")
                .replace("根据知识库", "")
                .replace("资料", "")
                .replaceAll("\\s+", " ")
                .strip();
        if (!compact.isBlank() && !compact.equals(question)) {
            queries.add(compact);
        }
        return queries.stream().distinct().limit(3).toList();
    }

    private List<String> parse(String reply) {
        String json = extractJson(reply);
        if (json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode queries = root.path("queries");
            if (!queries.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            queries.forEach(node -> {
                String value = node.asText("");
                if (!value.isBlank()) {
                    values.add(value.strip());
                }
            });
            return values.stream().distinct().limit(3).toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String extractJson(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        return start >= 0 && end > start ? value.substring(start, end + 1) : "";
    }

    private String prompt(String question) {
        return """
                你是知识库检索查询改写器。请把用户问题改写成 2-3 个适合向量检索的中文查询。
                只返回 JSON，不要解释。
                JSON 格式：{"queries":["查询1","查询2"]}

                用户问题：%s
                """.formatted(question);
    }
}
