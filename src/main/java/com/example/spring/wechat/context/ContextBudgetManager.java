package com.example.spring.wechat.context;

import org.springframework.stereotype.Component;

@Component
public class ContextBudgetManager {

    private final WechatContextProperties properties;
    private final TokenEstimator tokenEstimator;

    public ContextBudgetManager(WechatContextProperties properties, TokenEstimator tokenEstimator) {
        this.properties = properties;
        this.tokenEstimator = tokenEstimator;
    }

    public int contextBudgetTokens() {
        int available = availableInputTokens();
        return Math.max(1, (int) Math.floor(available * properties.maxInputRatio()));
    }

    public ContextBudgetReport report(String contextText, boolean compressed) {
        int available = availableInputTokens();
        int budget = Math.max(1, (int) Math.floor(available * properties.maxInputRatio()));
        return new ContextBudgetReport(
                properties.modelWindowTokens(),
                properties.outputReserveTokens(),
                properties.toolLoopReserveTokens(),
                available,
                budget,
                tokenEstimator.estimate(contextText),
                compressed);
    }

    private int availableInputTokens() {
        return Math.max(1,
                properties.modelWindowTokens()
                        - properties.outputReserveTokens()
                        - properties.toolLoopReserveTokens());
    }
}
