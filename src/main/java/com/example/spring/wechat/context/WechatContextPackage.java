package com.example.spring.wechat.context;

import java.util.List;

public record WechatContextPackage(
        RelevanceLevel relevance,
        String finalContextText,
        List<MemoryGraphNode> selectedNodes,
        ContextBudgetReport budgetReport) {

    public WechatContextPackage {
        relevance = relevance == null ? RelevanceLevel.WEAK : relevance;
        finalContextText = finalContextText == null ? "" : finalContextText.strip();
        selectedNodes = selectedNodes == null ? List.of() : List.copyOf(selectedNodes);
    }
}
