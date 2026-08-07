package com.example.spring.xhs.ingestion;

import java.util.Locale;
import java.util.Set;

public final class XhsCommentRiskClassifier {

    private static final Set<String> NEGATIVE_TERMS = Set.of(
            "\u4e0d\u597d", "\u5931\u671b", "\u95ee\u9898", "\u65e0\u6548", "\u8fc7\u654f",
            "\u7ea2\u80bf", "\u75bc", "\u6b3a\u9a97", "\u9000\u6b3e", "\u7ef4\u6743",
            "\u5047\u8d27", "\u8e29\u96f7", "\u907f\u96f7", "\u4e0d\u63a8\u8350");

    private XhsCommentRiskClassifier() {
    }

    public static Result classify(String content) {
        String text = content == null ? "" : content.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String term : NEGATIVE_TERMS) {
            if (text.contains(term)) {
                hits++;
            }
        }
        boolean negative = hits > 0;
        return new Result(negative ? "NEGATIVE" : "NEUTRAL", Math.min(100, hits * 25), negative);
    }

    public record Result(String sentiment, int riskScore, boolean negative) {
    }
}
