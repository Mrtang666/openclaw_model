package com.example.spring.wechat.context;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ContextCompressor {

    private static final String MARKER = "[已压缩]";

    private final TokenEstimator tokenEstimator;

    public ContextCompressor(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    public List<ContextSection> compress(List<ContextSection> sections, int budgetTokens) {
        List<ContextSection> result = new ArrayList<>(sections == null ? List.of() : sections);
        if (estimateContent(result) <= budgetTokens) {
            return result;
        }
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < result.size(); index++) {
            if (result.get(index).compressible()) {
                indexes.add(index);
            }
        }
        indexes.sort(Comparator.comparingInt((Integer index) -> result.get(index).compressionPriority()).reversed());
        for (Integer index : indexes) {
            if (estimateContent(result) <= budgetTokens) {
                break;
            }
            ContextSection section = result.get(index);
            int overBudget = estimateContent(result) - budgetTokens;
            int currentLength = tokenEstimator.estimate(section.content());
            int targetLength = Math.max(MARKER.length(), currentLength - overBudget - MARKER.length());
            result.set(index, section.withContent(truncate(section.content(), targetLength)));
        }
        return result;
    }

    private int estimateContent(List<ContextSection> sections) {
        return tokenEstimator.estimate(sections.stream()
                .map(ContextSection::content)
                .reduce("", String::concat));
    }

    private String truncate(String value, int maxChars) {
        String text = value == null ? "" : value.strip();
        if (text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= MARKER.length()) {
            return MARKER;
        }
        return text.substring(0, Math.max(0, maxChars - MARKER.length())).stripTrailing() + MARKER;
    }
}
