package com.example.spring.wechat.conversation.rag;

import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class RagRerankService {

    private static final Pattern ENGLISH_TOKEN = Pattern.compile("[A-Za-z0-9]+");
    private static final List<String> CHINESE_KEYWORDS = List.of(
            "流程", "架构", "设计", "工具", "项目", "知识库", "检索", "增强", "生成", "文档", "资料", "配置", "实现");

    public List<KnowledgeSearchResult> rank(String question, List<KnowledgeSearchResult> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<String> terms = queryTerms(question);
        List<RankedResult> ranked = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            KnowledgeSearchResult result = candidates.get(index);
            if (result != null) {
                ranked.add(new RankedResult(result, relevanceScore(result, terms), index));
            }
        }
        return ranked.stream()
                .sorted((left, right) -> {
                    int score = Double.compare(right.score(), left.score());
                    return score != 0 ? score : Integer.compare(left.index(), right.index());
                })
                .map(RankedResult::result)
                .toList();
    }

    private double relevanceScore(KnowledgeSearchResult result, List<String> terms) {
        double score = result.score();
        String title = normalize(result.title());
        String content = normalize(result.content());
        for (String term : terms) {
            String value = normalize(term);
            if (value.isBlank()) {
                continue;
            }
            if (title.contains(value)) {
                score += 0.12;
            }
            if (content.contains(value)) {
                score += 0.06;
            }
        }
        return score;
    }

    private List<String> queryTerms(String question) {
        String text = question == null ? "" : question;
        List<String> terms = new ArrayList<>();
        var matcher = ENGLISH_TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() > 1) {
                terms.add(token);
            }
        }
        for (String keyword : CHINESE_KEYWORDS) {
            if (text.contains(keyword)) {
                terms.add(keyword);
            }
        }
        return terms.stream().distinct().toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record RankedResult(KnowledgeSearchResult result, double score, int index) {
    }
}
