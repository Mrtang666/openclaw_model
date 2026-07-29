package com.example.spring.xhs.analysis;

import java.util.ArrayList;
import java.util.List;

public class RuleBasedXhsSemanticAnalyzer implements XhsSemanticAnalyzer {

    @Override
    public XhsSemanticAssessment analyze(XhsAnalysisCandidate candidate) {
        String text = ((candidate.title() == null ? "" : candidate.title()) + " "
                + (candidate.content() == null ? "" : candidate.content())).toLowerCase(java.util.Locale.ROOT);
        List<String> evidence = new ArrayList<>();
        int severity = 1;
        String category = "GENERAL";
        if (containsAny(text, "过敏", "红肿", "中毒", "受伤", "安全事故", "召回")) {
            category = "CONSUMER_SAFETY";
            severity = 5;
            evidence.add("文本包含产品或人身安全风险词");
        } else if (containsAny(text, "投诉", "欺骗", "虚假", "假货", "维权", "退款")) {
            category = "CONSUMER_COMPLAINT";
            severity = 4;
            evidence.add("文本包含明确投诉或维权表达");
        } else if (containsAny(text, "不好用", "失望", "踩雷", "避雷", "差评", "不推荐")) {
            category = "PRODUCT_EXPERIENCE";
            severity = 3;
            evidence.add("文本包含负面使用体验");
        }
        boolean negative = severity >= 3 || containsAny(text, "差", "问题", "失败", "生气");
        return new XhsSemanticAssessment(
                negative ? XhsSentiment.NEGATIVE : XhsSentiment.NEUTRAL,
                negative ? -0.7 : 0,
                List.of(category),
                category,
                severity,
                0.55,
                summary(candidate),
                evidence);
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String summary(XhsAnalysisCandidate candidate) {
        String text = candidate.content() == null || candidate.content().isBlank()
                ? candidate.title()
                : candidate.content();
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("\\s+", " ").strip();
        return cleaned.length() <= 160 ? cleaned : cleaned.substring(0, 160);
    }
}
